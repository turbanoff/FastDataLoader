package org.turbanov.loader;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Andrey Turbanov
 * @since 19.02.2017
 */
public class FastDataLoaderServer implements Disposable {
    private static final Logger log = Logger.getInstance(FastDataLoaderServer.class);

    private static final String ConfigurationPageDescriptor = "com.devexperts.dxconfig.descriptor.ConfigurationPageDescriptor";
    private static final String ScannableDomain = "com.devexperts.dxcore.descriptors.impl.ScannableDomain";
    private static final List<String> fieldAnnotations = Arrays.asList(ConfigurationPageDescriptor, ScannableDomain);

    private static final String ConditionTypeMarker = "com.devexperts.dxcore.api.hierarchy.conditions.ConditionTypeMarker";
    private static final String JsonAdapterMarker = "com.devexperts.dxcore.api.annotations.JsonAdapterMarker";
    private static final String JsonAdapterFor = "com.devexperts.dxcore.api.annotations.JsonAdapterFor";
    private static final String AccountGroupCategoryHolder = "com.devexperts.dxcore.entities.accountgroups.categories.repository.AccountGroupCategoryHolder";
    private static final String RegisteredJSONMutation = "com.devexperts.dxcore.tools.applications.console.hsettings.json.RegisteredJSONMutation";
    private static final String Component = "org.springframework.stereotype.Component";
    private static final List<String> classAnnotations = Arrays.asList(ConditionTypeMarker, JsonAdapterMarker, JsonAdapterFor, AccountGroupCategoryHolder, RegisteredJSONMutation, Component);

    private final Project project;
    private volatile ServerSocket serverSocket;

    public FastDataLoaderServer(Project project) {
        this.project = project;
    }

    public int startServer() {
        try {
            serverSocket = new ServerSocket(0);
        } catch (IOException e) {
            return 0;
        }
        log.info("Listening connection on port " + serverSocket.getLocalPort());
        Thread thread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    sendData(socket);
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        log.warn(e);
                    }
                } catch (Throwable e) {
                    log.error(e);
                }
            }
        }, "AnnotationDataSender-" + project.getName());
        thread.start();
        return serverSocket.getLocalPort();
    }

    private void sendData(Socket socket) throws IOException {
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        String moduleName = dis.readUTF();
        Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
        if (module == null) {
            log.warn("Unable to find module " + moduleName);
            return;
        }
        JsonObject data = ApplicationManager.getApplication().runReadAction((Computable<JsonObject>) () -> createData(project, module.getModuleWithDependenciesScope()));
        if (data == null) return;
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        dos.writeUTF(data.toString());
        dos.flush();

        dis.readByte();//make sure that data received
    }

    static JsonObject createData(Project project, GlobalSearchScope scope) {
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);

        List<PsiClass> resolvedFieldAnnotations = resolve(fieldAnnotations, psiFacade, scope);
        List<PsiClass> resolvedClassAnnotations = resolve(classAnnotations, psiFacade, scope);

        if (resolvedFieldAnnotations.isEmpty() && resolvedClassAnnotations.isEmpty()) {
            log.info("Unable to find annotations: " + fieldAnnotations + " or " + classAnnotations);
            return null;
        }

        //annotation name -> annotated classes
        JsonObject classes = findAnnotatedClasses(scope, resolvedClassAnnotations);
        //annotation name -> list of class&field
        JsonObject fields = findAnnotatedFields(scope, resolvedFieldAnnotations);

        JsonObject result = new JsonObject();
        result.add("classes", classes);
        result.add("fields", fields);
        return result;
    }

    private static List<PsiClass> resolve(List<String> annotations, JavaPsiFacade psiFacade, GlobalSearchScope scope) {
        List<PsiClass> resolved = new ArrayList<>(annotations.size());
        for (String annotationName : annotations) {
            PsiClass annotation = psiFacade.findClass(annotationName, scope);
            resolved.add(annotation);
        }
        return resolved;
    }

    @NotNull
    private static JsonObject findAnnotatedFields(GlobalSearchScope scope, List<PsiClass> resolvedFieldAnnotations) {
        JsonObject alLFields = new JsonObject();

        for (PsiClass psiClass : resolvedFieldAnnotations) {
            if (psiClass.getQualifiedName() == null) continue;
            Query<PsiReference> query = ReferencesSearch.search(psiClass, scope);
            JsonArray fields = new JsonArray();
            query.forEach(annotationReference -> {
                JsonObject oneField = getAppliedField(annotationReference);
                if (oneField != null) {
                    fields.add(oneField);
                }
            });
            if (fields.size() != 0) {
                alLFields.add(psiClass.getQualifiedName(), fields);
            }
        }
        return alLFields;
    }

    private static JsonObject findAnnotatedClasses(GlobalSearchScope scope, List<PsiClass> resolvedClassAnnotations) {
        JsonObject classesObject = new JsonObject();
        for (PsiClass psiClass : resolvedClassAnnotations) {
            if (psiClass.getQualifiedName() == null) continue;
            Query<PsiReference> query = ReferencesSearch.search(psiClass, scope);
            JsonArray classes = new JsonArray();
            query.forEach(annotationReference -> {
                String className = getAppliedClassName(annotationReference);
                if (className != null) {
                    classes.add(className);
                }
            });
            if (classes.size() != 0) {
                classesObject.add(psiClass.getQualifiedName(), classes);
            }
        }
        return classesObject;
    }

    private static String getAppliedClassName(PsiReference reference) {
        if (!(reference instanceof PsiJavaCodeReferenceElement)) {
            return null;
        }
        PsiClass clazz = PsiTreeUtil.getParentOfType((PsiJavaCodeReferenceElement) reference, PsiClass.class);
        if (clazz == null) {
            return null;
        }
        return clazz.getQualifiedName();
    }

    @Nullable
    private static JsonObject getAppliedField(PsiReference reference) {
        if (!(reference instanceof PsiJavaCodeReferenceElement)) {
            return null;
        }
        PsiField field = PsiTreeUtil.getParentOfType((PsiJavaCodeReferenceElement) reference, PsiField.class);
        if (field == null) {
            return null;
        }
        String fieldName = field.getName();
        if (fieldName == null) return null;
        PsiClass clazz = field.getContainingClass();
        if (clazz == null || clazz.getQualifiedName() == null) {
            return null;
        }

        JsonObject result = new JsonObject();
        result.addProperty("c", clazz.getQualifiedName());
        result.addProperty("f", fieldName);
        return result;
    }

    @Override
    public void dispose() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignore) {
            }
        }
    }
}
