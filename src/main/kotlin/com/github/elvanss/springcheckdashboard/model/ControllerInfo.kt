package com.github.elvanss.springcheckdashboard.model

data class ControllerInfo (
    val moduleName: String,
    val controllerName: String,
    val methods: List<EndpointInfo>
)