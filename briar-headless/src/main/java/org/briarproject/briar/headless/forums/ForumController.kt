package org.briarproject.briar.headless.forums

import io.javalin.http.Context

interface ForumController {

    fun list(ctx: Context): Context

    fun create(ctx: Context): Context

    fun createPost(ctx: Context): Context

    fun addUser(ctx: Context): Context

    fun listUsers(ctx: Context):Context

}
