package com.example.i230529_a1

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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

data class MarketItem(val id: String, val title: String, val price: String, val seller: String, val createdAt: Long)

class MarketplaceActivity : AppCompatActivity() {
    private var itemsQuery: Query? = null
    private var itemsListener: ValueEventListener? = null
    private lateinit var adapter: MarketItemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_marketplace)
        open(R.id.searchButton, SearchActivity::class.java)
        open(R.id.chatsButton, ChatsActivity::class.java)
        open(R.id.homeTab, HomeActivity::class.java)
        open(R.id.friendsTab, FriendsActivity::class.java)
        open(R.id.notificationsTab, NotificationsActivity::class.java)
        open(R.id.menuTab, MenuActivity::class.java)
        findViewById<View>(R.id.sellButton).setOnClickListener { showAddItemDialog() }
        adapter = MarketItemAdapter()
        findViewById<RecyclerView>(R.id.marketplaceRecyclerView).apply {
            layoutManager = GridLayoutManager(this@MarketplaceActivity, 2)
            adapter = this@MarketplaceActivity.adapter
        }
        itemsQuery = FirebaseData.marketplaceItems.orderByChild("createdAt").limitToLast(100)
        val observer = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val items = snapshot.children.mapNotNull { item ->
                    val title = item.child("title").getValue(String::class.java) ?: return@mapNotNull null
                    MarketItem(
                        id = item.key.orEmpty(),
                        title = title,
                        price = item.child("price").getValue(String::class.java).orEmpty(),
                        seller = item.child("ownerName").getValue(String::class.java).orEmpty(),
                        createdAt = item.child("createdAt").getValue(Long::class.java) ?: 0L
                    )
                }.sortedByDescending { it.createdAt }
                adapter.setItems(items)
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Toast.makeText(this@MarketplaceActivity, error.message, Toast.LENGTH_LONG).show()
            }
        }
        itemsListener = observer
        itemsQuery?.addValueEventListener(observer)
    }

    private fun showAddItemDialog() {
        val title = EditText(this).apply { hint = "Item name" }
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
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid == null || itemName.isBlank() || itemPrice.isBlank()) {
                    Toast.makeText(this, "Enter an item name and price while signed in", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                FirebaseData.users.child(uid).child("name").get().addOnSuccessListener { owner ->
                    val reference = FirebaseData.marketplaceItems.push()
                    reference.setValue(mapOf("ownerId" to uid, "ownerName" to owner.getValue(String::class.java).orEmpty(), "title" to itemName, "price" to itemPrice, "createdAt" to mapOf(".sv" to "timestamp")))
                        .addOnSuccessListener { dialog.dismiss() }
                        .addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not publish item", Toast.LENGTH_LONG).show() }
                }
            }
        }
        dialog.show()
    }

    private fun open(viewId: Int, target: Class<*>) {
        findViewById<View>(viewId).setOnClickListener { startActivity(Intent(this, target)) }
    }

    override fun onDestroy() {
        itemsListener?.let { itemsQuery?.removeEventListener(it) }
        super.onDestroy()
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
