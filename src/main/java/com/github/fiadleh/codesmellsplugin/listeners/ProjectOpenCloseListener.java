package com.github.fiadleh.codesmellsplugin.listeners;


import com.github.fiadleh.codesmellsplugin.util.CacheManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

/**
 * Listener to invoke recreating cache each time a project opened
 *
 * @author Firas Adleh
 */
public class ProjectOpenCloseListener implements ProjectManagerListener {

    /**
     * Called when project opens
     *
     * @param project currently opened project
     */
    @Override
    public void projectOpened(@NotNull Project project) {
        // Ensure this isn't part of testing
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            return;
        }

        // reset the cache to recreate it for the new project
        CacheManager.resetIsCacheReady();

        // start creating cache after the project is completely loaded and indexed
        DumbService.getInstance(project).smartInvokeLater(
                () ->
                    CacheManager.createClassesListCache(project)
                , ModalityState.any()
        );

    }
}