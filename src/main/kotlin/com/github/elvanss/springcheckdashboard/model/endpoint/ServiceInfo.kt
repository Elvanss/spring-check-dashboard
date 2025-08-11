package com.github.elvanss.springcheckdashboard.model.endpoint

data class ServiceInfo(
    val name: String,
    val controllers: List<EndpointInfo>
)