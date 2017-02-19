package org.turbanov.loader;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

/**
 * @author Andrey Turbanov
 * @since 19.02.2017
 */
public class FastDataLoaderJavaProgramPatcher extends JavaProgramPatcher {
    private static final Logger log = Logger.getInstance(FastDataLoaderJavaProgramPatcher.class);

    @Override
    public void patchJavaParameters(Executor executor, RunProfile configuration, JavaParameters javaParameters) {
        if (configuration instanceof ModuleBasedConfiguration) {
            Module module = ((ModuleBasedConfiguration) configuration).getConfigurationModule().getModule();
            if (module == null) return;
            Project project = module.getProject();
            Integer port = project.getUserData(FastDataLoaderComponent.portNumberKey);
            if (port == null) return;

            String moduleParameter = "-Dfast.data.loader.idea.module.name=" + module.getName();
            String portParameter = "-Dfast.data.loader.server.port=" + port;
            log.info("Add VM options " + moduleParameter + ", " + portParameter + " for run " + configuration.getName());
            javaParameters.getVMParametersList().add(moduleParameter);
            javaParameters.getVMParametersList().add(portParameter);
        }
    }
}
