package com.glimpse.app.data.firebase

import android.util.Log
import com.glimpse.app.data.model.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

object FirebaseSync {
    private const val TAG = "FirebaseSync"
    private val database get() = FirebaseDatabase.getInstance().reference

    fun listenToCurrentMessage(onMessage: (Message?) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onMessage(snapshot.getValue(Message::class.java))
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenToCurrentMessage cancelled", error.toException())
            }
        }
        database.child("shared").child("current_message").addValueEventListener(listener)
        return listener
    }

    fun removeCurrentMessageListener(listener: ValueEventListener) {
        database.child("shared").child("current_message").removeEventListener(listener)
    }

    fun addReaction(emoji: String, onComplete: (Boolean) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            onComplete(false)
            return
        }
        val reactionsRef = database.child("shared/current_message/reactions/$emoji")

        reactionsRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                @Suppress("UNCHECKED_CAST")
                val userIds = (mutableData.value as? List<String>).orEmpty()
                if (userId !in userIds) {
                    mutableData.value = userIds + userId
                }
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null) {
                    Log.e(TAG, "addReaction failed", error.toException())
                }
                onComplete(error == null && committed)
            }
        })
    }
}
