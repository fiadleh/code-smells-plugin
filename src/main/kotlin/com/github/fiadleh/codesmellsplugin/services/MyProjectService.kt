package com.github.fiadleh.codesmellsplugin.services

import com.github.fiadleh.codesmellsplugin.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
