/**
 * Copyright Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.codelab.friendlychat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.firebase.ui.database.SnapshotParser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.codelab.friendlychat.databinding.ActivityMainBinding
import com.google.firebase.codelab.friendlychat.model.FriendlyMessage
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import dev.leonardpark.emoji.EmojiManager
import dev.leonardpark.emoji.EmojiPopup
import dev.leonardpark.emoji.emoji.Emoji
import dev.leonardpark.emoji.emojicompat.EmojiImageView
import dev.leonardpark.emoji.google.GoogleEmojiProvider
import dev.leonardpark.emoji.listeners.OnEmojiBackspaceClickListener
import dev.leonardpark.emoji.listeners.OnEmojiClickListener
import dev.leonardpark.emoji.listeners.OnEmojiPopupDismissListener
import dev.leonardpark.emoji.listeners.OnEmojiPopupShownListener
import dev.leonardpark.emoji.listeners.OnSoftKeyboardCloseListener
import dev.leonardpark.emoji.listeners.OnSoftKeyboardOpenListener


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var manager: LinearLayoutManager
    private lateinit var rootView: ViewGroup
    private var emojiPopup: EmojiPopup? = null
    lateinit var emojiButton: ImageView

    // Firebase instance variables
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase
    private lateinit var adapter: FriendlyMessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        EmojiManager.install(GoogleEmojiProvider())

        // This codelab uses View Binding
        // See: https://developer.android.com/topic/libraries/view-binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        rootView = binding.mainActivityRootview
        emojiButton = binding.mainActivityEmoji

        // When running in debug mode, connect to the Firebase Emulator Suite
        // "10.0.2.2" is a special value which allows the Android emulator to
        // connect to "localhost" on the host computer. The port values are
        // defined in the firebase.json file.
//        if (BuildConfig.DEBUG) {
//            Firebase.database.useEmulator("192.168.0.113", 9000)
//            Firebase.auth.useEmulator("192.168.0.113", 9099)
//            Firebase.storage.useEmulator("192.168.0.113", 9199)
//        }

        // Initialize Firebase Auth and check if the user is signed in
        auth = Firebase.auth
        if (auth.currentUser == null) {
            // Not signed in, launch the Sign In activity
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        }

        // Initialize Realtime Database
        db = Firebase.database
        val messagesRef = db.reference.child(MESSAGES_CHILD)

        // The FirebaseRecyclerAdapter class and options come from the FirebaseUI library
        // See: https://github.com/firebase/FirebaseUI-Android
        val snapshotParser: SnapshotParser<FriendlyMessage?> =
            SnapshotParser<FriendlyMessage?> { snapshot: DataSnapshot ->
                val chat = snapshot.getValue(FriendlyMessage::class.java)
                chat?.let {
                    it.uid = snapshot.key
                }
                chat!!
            }
        val options = FirebaseRecyclerOptions.Builder<FriendlyMessage>()
            .setQuery(messagesRef, snapshotParser)
            .build()
        adapter = FriendlyMessageAdapter(options, getUserName())
        binding.progressBar.visibility = ProgressBar.INVISIBLE
        manager = LinearLayoutManager(this)
        manager.stackFromEnd = true
        binding.messageRecyclerView.layoutManager = manager
        binding.messageRecyclerView.adapter = adapter

        adapter.onDeleteClick = {
            if(it.uid != null) {
                val message = messagesRef.child(it.uid!!)
                message.removeValue()
            }
        }

        // Scroll down when a new message arrives
        // See MyScrollToBottomObserver for details
        adapter.registerAdapterDataObserver(
            MyScrollToBottomObserver(binding.messageRecyclerView, adapter, manager)
        )

        // Disable the send button when there's no text in the input field
        // See MyButtonObserver for details
        binding.messageEditText.addTextChangedListener(MyButtonObserver(binding.sendButton))

        // When the send button is clicked, send a text message
        binding.sendButton.setOnClickListener {
            val friendlyMessage = FriendlyMessage(
                binding.messageEditText.text.toString(),
                getUserName(),
                getPhotoUrl(),
                null
            )
            db.reference.child(MESSAGES_CHILD).push().setValue(friendlyMessage)
            binding.messageEditText.setText("")
        }

        // When the image button is clicked, launch the image picker
        emojiButton.setOnClickListener {
            emojiPopup!!.toggle()
        }

        setUpEmojiPopup()
    }

    private fun setUpEmojiPopup() {
        emojiPopup = EmojiPopup.Builder.fromRootView(rootView)
            .setOnEmojiBackspaceClickListener(object : OnEmojiBackspaceClickListener {
                override fun onEmojiBackspaceClick(v: View) {
                    Log.d(TAG, "Clicked on Backspace")
                }
            })
            .setOnEmojiClickListener(object : OnEmojiClickListener {
                override fun onEmojiClick(imageView: EmojiImageView, emoji: Emoji) {
                    Log.d(TAG, "Clicked on emoji")
                }
            })
            .setOnEmojiPopupShownListener(object : OnEmojiPopupShownListener {
                override fun onEmojiPopupShown() {
                    emojiButton.setImageResource(R.drawable.ic_keyboard)
                }
            })
            .setOnSoftKeyboardOpenListener(object : OnSoftKeyboardOpenListener {
                override fun onKeyboardOpen(keyBoardHeight: Int) {
                    Log.d(
                        TAG, "Opened soft keyboard"
                    )
                }
            })
            .setOnEmojiPopupDismissListener(object : OnEmojiPopupDismissListener {
                override fun onEmojiPopupDismiss() {
                    emojiButton.setImageResource(R.drawable.outline_emoji_emotions_24)
                }
            })
            .setOnSoftKeyboardCloseListener(object : OnSoftKeyboardCloseListener {
                override fun onKeyboardClose() {
                    Log.d(TAG, "Closed soft keyboard")
                }
            })
            .build(binding.messageEditText)


    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in.
        if (auth.currentUser == null) {
            // Not signed in, launch the Sign In activity
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        }
    }

    public override fun onPause() {
        adapter.stopListening()
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
        adapter.startListening()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sign_out_menu -> {
                signOut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun signOut() {
        AuthUI.getInstance().signOut(this)
        startActivity(Intent(this, SignInActivity::class.java))
        finish()
    }

    private fun getPhotoUrl(): String? {
        val user = auth.currentUser
        return user?.photoUrl?.toString()
    }

    private fun getUserName(): String? {
        val user = auth.currentUser
        return if (user != null) {
            user.displayName
        } else ANONYMOUS
    }

    companion object {
        private const val TAG = "MainActivity"
        const val MESSAGES_CHILD = "messages"
        const val ANONYMOUS = "anonymous"

    }
}
