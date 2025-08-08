package com.github.elvanss.springcheckdashboard.model

data class ServiceInfo(
    val name: String,
    val controllers: List<EndpointInfo>
)