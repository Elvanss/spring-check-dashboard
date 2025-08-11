package com.github.elvanss.springcheckdashboard.services.endpoint

import com.intellij.openapi.components.Service

@Service(Service.Level.PROJECT)
class SpringEndpointService() {

    private val endpointsByFile = mutableMapOf<String, List<String>>()

    fun updateEndpoints(filePath: String, endpoints: List<String>) {
        endpointsByFile[filePath] = endpoints
        println("Updated endpoints for $filePath: $endpoints")
    }
}
