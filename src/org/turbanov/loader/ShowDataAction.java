package org.turbanov.loader;

import com.google.gson.JsonObject;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ui.UIUtil;

/**
 * @author Andrey Turbanov
 * @since 19.02.2017
 */
public class ShowDataAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        JsonObject result = FastDataLoaderServer.createData(project, GlobalSearchScope.projectScope(project));
        if (result == null) return;
        Messages.showMessageDialog(result.toString(), "AbstractDataLoader Data", UIUtil.getInformationIcon());
    }

}
