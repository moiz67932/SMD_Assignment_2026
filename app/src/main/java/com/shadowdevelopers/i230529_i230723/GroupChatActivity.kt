package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Group chat: text messages for every member. groupMessages/{groupId}/{messageId} = {senderId, senderName, text, timestamp}. */
class GroupChatActivity : AppCompatActivity() {
    private data class Line(val id: String, val sender: String, val senderName: String, val text: String)

    private val me by lazy { Backend.uid.orEmpty() }
    private val groupId by lazy { intent.getStringExtra("group_id").orEmpty() }
    private val listeners = ListenerBag()
    private val lines = mutableListOf<Line>()
    private lateinit var recycler: RecyclerView
    private lateinit var input: EditText
    private var myName = "Me"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (me.isBlank() || groupId.isBlank()) {
            finish()
            return
        }
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(getColor(R.color.screen_background)) }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(8), 0) }
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_back); contentDescription = "Back"
            setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        bar.addView(TextView(this).apply {
            text = intent.getStringExtra("group_name") ?: "Group"
            textSize = 19f; setTextColor(getColor(R.color.text_primary)); setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@GroupChatActivity).apply { stackFromEnd = true }
            adapter = LineAdapter()
        }
        val inputRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(6), dp(10), dp(8)) }
        input = EditText(this).apply {
            hint = "Message"
            setBackgroundResource(R.drawable.rounded_soft)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
            filters = arrayOf(android.text.InputFilter.LengthFilter(1000))
        }
        val send = ImageView(this).apply {
            setImageResource(R.drawable.ic_send); contentDescription = "Send"
            setColorFilter(getColor(R.color.primary)); setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { sendMessage() }
        }
        inputRow.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(send, LinearLayout.LayoutParams(dp(46), dp(46)))
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        root.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(inputRow)
        setContentView(root)
        ProfileCache.get(me) { myName = it.name }
    }

    override fun onStart() {
        super.onStart()
        if (me.isBlank() || groupId.isBlank()) return
        // GET groups/messages.php every 3 s while open (cached in SQLite for offline reading).
        listeners.poll(3_000) { load() }
    }

    private fun load() {
        Backend.call({
            runCatching { Backend.api.get("groups/messages.php?group_id=$groupId").getJSONArray("messages") }
                .onSuccess { Backend.store.putEntity(me, "group_messages", groupId, it.toString()) }
                .getOrNull() ?: Backend.store.entity(me, "group_messages", groupId)?.let { org.json.JSONArray(it) } ?: org.json.JSONArray()
        }) { result ->
            val list = result.getOrNull() ?: return@call
            val pending = lines.filter { it.id.startsWith("pending:") }
            lines.clear()
            (0 until list.length()).map { list.getJSONObject(it) }.forEach {
                lines += Line(it.getString("id"), it.getString("sender_id"), it.optString("sender_name"), it.optString("body"))
            }
            lines.addAll(pending.filter { p -> lines.none { it.id == p.id.removePrefix("pending:") } })
            recycler.adapter?.notifyDataSetChanged()
            if (lines.isNotEmpty()) recycler.scrollToPosition(lines.lastIndex)
        }
    }

    override fun onStop() {
        listeners.removeAll()
        super.onStop()
    }

    /** POST groups/send.php (queued, so it also works offline); shown at once as "sending…". */
    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isBlank()) return
        val id = Backend.newId()
        input.setText("")
        lines += Line("pending:$id", me, myName, "$text  · sending…")
        recycler.adapter?.notifyDataSetChanged()
        recycler.scrollToPosition(lines.lastIndex)
        Backend.call({ Backend.enqueue("group_message", id, org.json.JSONObject().put("id", id), "group.send", org.json.JSONObject().put("id", id).put("group_id", groupId).put("body", text)) }) { result ->
            result.onFailure { Toast.makeText(this, Forms.message(it as? Exception, "Could not send"), Toast.LENGTH_LONG).show() }
        }
    }

    /** Each row is full width; the bubble inside it sits on the right for my messages and on the left for others. */
    private inner class LineAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = lines.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val density = resources.displayMetrics.density
            val row = LinearLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding((10 * density).toInt(), (3 * density).toInt(), (10 * density).toInt(), (3 * density).toInt())
            }
            val bubble = TextView(parent.context).apply {
                setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
                textSize = 15f
                maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
            }
            row.addView(bubble, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return object : RecyclerView.ViewHolder(row) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val line = lines[position]
            val mine = line.sender == me
            val row = holder.itemView as LinearLayout
            val bubble = row.getChildAt(0) as TextView
            row.gravity = if (mine) Gravity.END else Gravity.START
            bubble.text = if (mine) line.text else "${line.senderName.substringBefore(" ")}: ${line.text}"
            bubble.setTextColor(getColor(if (mine) R.color.white else R.color.text_primary))
            bubble.setBackgroundResource(if (mine) R.drawable.rounded_teal else R.drawable.rounded_soft)
        }
    }
}
