package com.github.elvanss.springcheckdashboard.model.endpoint

data class ControllerInfo (
    val moduleName: String,
    val controllerName: String,
    val methods: List<EndpointInfo>
)