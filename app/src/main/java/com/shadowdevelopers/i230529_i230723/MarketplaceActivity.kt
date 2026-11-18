package com.shadowdevelopers.i230529_i230723

import com.shadowdevelopers.i230529_i230723.data.Backend
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class MarketItem(val id: String, val title: String, val price: String, val seller: String, val createdAt: Long)

class MarketplaceActivity : AppCompatActivity() {
    private val badges by lazy { TabBadges(this) }
    private lateinit var adapter: MarketItemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_marketplace)
        open(R.id.searchButton, SearchActivity::class.java)
        open(R.id.chatsButton, ChatsActivity::class.java)
        Nav.tab(this, R.id.homeTab, HomeActivity::class.java)
        Nav.tab(this, R.id.friendsTab, FriendsActivity::class.java)
        Nav.tab(this, R.id.notificationsTab, NotificationsActivity::class.java)
        Nav.tab(this, R.id.menuTab, MenuActivity::class.java)
        findViewById<View>(R.id.sellButton).setOnClickListener { showAddItemDialog() }
        adapter = MarketItemAdapter()
        findViewById<RecyclerView>(R.id.marketplaceRecyclerView).apply {
            layoutManager = GridLayoutManager(this@MarketplaceActivity, 2)
            adapter = this@MarketplaceActivity.adapter
        }
        loadItems()
    }

    /** GET market/list.php (cached in SQLite, so the items also show offline). */
    private fun loadItems() {
        val me = Backend.uid ?: return
        Backend.call({
            runCatching { Backend.api.get("market/list.php").getJSONArray("items") }.onSuccess { Backend.store.putEntity(me, "market", "all", it.toString()) }
                .getOrNull() ?: Backend.store.entity(me, "market", "all")?.let { org.json.JSONArray(it) } ?: org.json.JSONArray()
        }) { result ->
            val list = result.getOrNull() ?: return@call
            val items = (0 until list.length()).map { list.getJSONObject(it) }.map {
                MarketItem(it.getString("id"), it.optString("title"), it.optString("price"), it.optString("seller_name"), TimeFormat.parse(it.optString("created_at")))
            }
            adapter.setItems(items)
            findViewById<TextView>(R.id.marketplaceCount).text = if (items.size == 1) "1 item" else "${items.size} items"
        }
    }

    private fun showAddItemDialog() {
        val title = EditText(this).apply {
            hint = "Item name"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(android.text.InputFilter.LengthFilter(80))
        }
        val price = EditText(this).apply { hint = "Price"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
            addView(title)
            addView(price)
        }
        val dialog = AlertDialog.Builder(this).setTitle("Sell an item").setView(form)
            .setPositiveButton("Publish", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val itemName = title.text.toString().trim()
                val itemPrice = price.text.toString().trim()
                val uid = Backend.uid
                if (uid == null || itemName.isBlank() || itemPrice.isBlank()) {
                    Toast.makeText(this, "Enter an item name and price while signed in", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // POST market/create.php (queued).
                val id = Backend.newId()
                Backend.call({ Backend.enqueue("market_item", id, org.json.JSONObject().put("id", id), "market.create",
                    org.json.JSONObject().put("id", id).put("title", itemName).put("price", itemPrice)) }) { result ->
                    result.onSuccess { dialog.dismiss(); android.os.Handler(mainLooper).postDelayed({ loadItems() }, 800) }
                        .onFailure { Toast.makeText(this, Forms.message(it as? Exception, "Could not publish item"), Toast.LENGTH_LONG).show() }
                }
            }
        }
        dialog.show()
    }

    private fun open(viewId: Int, target: Class<*>) {
        findViewById<View>(viewId).setOnClickListener { Nav.goTab(this, target) }
    }

    override fun onStart() {
        super.onStart()
        badges.start()
    }

    override fun onStop() {
        badges.stop()
        super.onStop()
    }

}

class MarketItemAdapter : RecyclerView.Adapter<MarketItemAdapter.MarketItemViewHolder>() {
    private val items = mutableListOf<MarketItem>()

    fun setItems(list: List<MarketItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MarketItemViewHolder {
        return MarketItemViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_marketplace, parent, false))
    }

    override fun onBindViewHolder(holder: MarketItemViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    class MarketItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: MarketItem) {
            itemView.findViewById<TextView>(R.id.itemTitle).text = item.title
            itemView.findViewById<TextView>(R.id.itemPrice).text = item.price
            itemView.findViewById<TextView>(R.id.itemSeller).text = item.seller
        }
    }
}
