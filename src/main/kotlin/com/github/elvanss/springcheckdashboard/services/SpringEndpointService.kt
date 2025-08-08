package com.github.elvanss.springcheckdashboard.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class SpringEndpointService(private val project: Project) {

    private val endpointsByFile = mutableMapOf<String, List<String>>()

    fun updateEndpoints(filePath: String, endpoints: List<String>) {
        endpointsByFile[filePath] = endpoints
        println("Updated endpoints for $filePath: $endpoints")
    }

    fun getAllEndpoints(): List<String> {
        return endpointsByFile.values.flatten()
    }

    fun clear() {
        endpointsByFile.clear()
    }
}
