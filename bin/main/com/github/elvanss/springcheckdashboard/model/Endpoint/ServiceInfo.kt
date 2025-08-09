package com.github.elvanss.springcheckdashboard.model.Endpoint

data class ServiceInfo(
    val name: String,
    val controllers: List<EndpointInfo>
)