package com.github.elvanss.springcheckdashboard.model.service

import com.intellij.openapi.module.Module

data class ServiceInfo(
    val module: Module,
    val moduleName: String,
    val serviceName: String,
    val mainClassFqn: String,
    val port: Int?
)
