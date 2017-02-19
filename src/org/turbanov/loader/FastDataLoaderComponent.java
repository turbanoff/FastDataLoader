package org.turbanov.loader;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;

/**
 * @author Andrey Turbanov
 * @since 19.02.2017
 */
public class FastDataLoaderComponent extends ApplicationComponent.Adapter {
    public static final Key<Integer> portNumberKey = new Key<>("FAST_DATA_LOADER_SERVER_PORT");

    @Override
    public void initComponent() {
        ProjectManager manager = ProjectManager.getInstance();
        manager.addProjectManagerListener(new ProjectManagerListener() {
            @Override
            public void projectOpened(Project project) {
                if (isDxCore(project)) {
                    FastDataLoaderServer server = new FastDataLoaderServer(project);
                    int portNumber = server.startServer();
                    if (portNumber != 0) {
                        project.putUserData(portNumberKey, portNumber);
                        Disposer.register(project, server);
                    }
                }
            }
        });
    }

    private boolean isDxCore(Project project) {
        return true;
    }
}
