package com.github.elvanss.springcheckdashboard.model

data class EndpointInfo(
    val httpMethod: String,
    val path: String,
    val methodName: String
)