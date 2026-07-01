package org.briarproject.briar.headless.forums

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import okhttp3.internal.userAgent
import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.bramble.api.db.DbException
import org.briarproject.bramble.api.db.TransactionManager
import org.briarproject.bramble.api.identity.IdentityManager
import org.briarproject.bramble.api.sync.GroupId
import org.briarproject.bramble.api.sync.Message
import org.briarproject.bramble.api.sync.MessageId
import org.briarproject.bramble.api.system.Clock
import org.briarproject.bramble.util.ByteUtils
import org.briarproject.bramble.util.StringUtils.utf8IsTooLong
import org.briarproject.briar.api.blog.BlogConstants
import org.briarproject.briar.api.blog.BlogPostHeader
import org.briarproject.briar.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH
import org.briarproject.briar.api.forum.ForumManager
import org.briarproject.briar.api.forum.ForumPost
import org.briarproject.briar.api.forum.ForumPostFactory
import org.briarproject.briar.api.forum.ForumSharingManager
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationManager
import org.briarproject.briar.headless.getContactIdFromPathParam
import org.briarproject.briar.headless.getFromJson
import java.nio.charset.Charset
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
@Singleton
internal class ForumControllerImpl
@Inject
constructor(private val forumManager: ForumManager,private val forumSharingManager: ForumSharingManager,
            private val forumPostFactory: ForumPostFactory, private val db: TransactionManager,
            private val identityManager: IdentityManager, private val objectMapper: ObjectMapper, private val clock: Clock) :
    ForumController {

    override fun list(ctx: Context): Context {

        return ctx.json(forumManager.forums.output())
    }



    override fun create(ctx: Context): Context {
        val name = ctx.getFromJson(objectMapper, "name")
        if (utf8IsTooLong(name, MAX_FORUM_NAME_LENGTH))
            throw BadRequestResponse("Forum name is too long")
        return ctx.json(forumManager.addForum(name).output())
    }

    override fun createPost(ctx: Context): Context{
        val text = ctx.getFromJson(objectMapper, "text")
        if (utf8IsTooLong(text, BlogConstants.MAX_BLOG_POST_TEXT_LENGTH))
            throw BadRequestResponse("Forum post text is too long")

        val author = identityManager.localAuthor
        var forumID:GroupId?=null
        val forumName = ctx.getFromJson(objectMapper,"name")
        forumManager.forums.forEach{
            if(it.name.equals(forumName)){
                forumID=it.group.id
            }

        }
        val now = clock.currentTimeMillis()

        val forumPost=forumPostFactory.createPost(forumID,now,null,author,text)

        return ctx.json(forumManager.addLocalPost(forumPost))

    }

    override fun addUser(ctx: Context): Context{
        var forumID:GroupId?= getGroupIdByName(ctx.getFromJson(objectMapper,"name"))
            ?: return ctx.json("Forum not found")
        val contactId = convertContactString(ctx.getFromJson(objectMapper,"contactId"))
        val invitationMessage=ctx.getFromJson(objectMapper,"invitationMessage")
        try {
            return ctx.json(forumSharingManager.sendInvitation(forumID, contactId, invitationMessage))
        }
        catch (e:Exception){
            return ctx.json(e.localizedMessage)
        }
    }

    override fun listUsers(ctx: Context):Context{
        var forumID:GroupId?= getGroupIdByName(ctx.getFromJson(objectMapper,"name"))
            ?: return ctx.json("Forum not found")
        return ctx.json(forumSharingManager.getSharedWith(forumID))
    }


    private fun getGroupIdByName(forumName:String):GroupId?{
        forumManager.forums.forEach{
            if(it.name.equals(forumName)){
                return it.group.id
            }

        }
        return null
    }

    private fun convertContactString(contactString:String): ContactId {

        val contactInt = try {
            Integer.parseInt(contactString)
        } catch (e: NumberFormatException) {
            throw NotFoundResponse()
        }
        return ContactId(contactInt)
    }



}
