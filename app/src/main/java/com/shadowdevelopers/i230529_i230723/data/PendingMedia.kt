package com.shadowdevelopers.i230529_i230723.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID

/** Stage picker/camera bytes in persistent account storage before queue insertion. */
object PendingMedia {
    fun copy(context:Context,account:String,uri:Uri):File {
        require(account.matches(Regex("[a-zA-Z0-9_-]+")))
        val root=File(context.filesDir,"pending_media/$account")
        check(root.mkdirs() || root.isDirectory)
        val temp=File(root,UUID.randomUUID().toString()+".part")
        val target=File(root,temp.name.removeSuffix(".part"))
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    val buffer=ByteArray(64*1024);var total=0L
                    while(true){val n=input.read(buffer);if(n<0)break;total+=n
                        if(total>25L*1024*1024)throw IOException("Attachment exceeds 25 MB")
                        output.write(buffer,0,n)}
                    output.fd.sync()
                }
            } ?: throw IOException("Selected attachment is unavailable")
            if(temp.length()==0L)throw IOException("Attachment is empty")
            check(temp.renameTo(target)){"Could not persist attachment"}
            return target
        }catch(e:Exception){temp.delete();throw e}
    }
    fun resolve(context:Context,account:String,path:String):File {
        val root=File(context.filesDir,"pending_media/$account").canonicalFile
        val file=File(path).canonicalFile
        require(file.parentFile==root && file.isFile){"Pending attachment is unavailable"}
        return file
    }
}
