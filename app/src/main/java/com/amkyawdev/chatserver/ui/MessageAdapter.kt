package com.amkyawdev.chatserver.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.amkyawdev.chatserver.R
import com.amkyawdev.chatserver.chat.ChatMessage
import com.amkyawdev.chatserver.databinding.ItemMessageBinding
import com.amkyawdev.chatserver.databinding.ItemMessageMineBinding

/**
 * Renders the transcript with two row types: cyan-bordered bubbles for other
 * participants and neon-green bubbles for local messages.
 *
 * Bubble width is capped at 78% of the list width here (rather than in XML)
 * because the cap is relative to the parent, not to a fixed dp value.
 */
class MessageAdapter(
    private val onLongPress: (ChatMessage) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ChatMessage>()
    private val animated = mutableSetOf<Long>()

    /** Replaces the transcript and keeps the list pinned to the newest row. */
    fun submit(messages: List<ChatMessage>) {
        items.clear()
        items.addAll(messages)
        animated.retainAll(messages.mapTo(mutableSetOf()) { it.id })
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position].isMine) TYPE_MINE else TYPE_OTHER

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_MINE) {
            MineHolder(ItemMessageMineBinding.inflate(inflater, parent, false))
        } else {
            OtherHolder(ItemMessageBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = items[position]
        val maxWidth = (holder.itemView.resources.displayMetrics.widthPixels * MAX_WIDTH_PCT).toInt()

        when (holder) {
            is OtherHolder -> {
                holder.binding.txtSender.text = "[${message.sender}]"
                holder.binding.txtMessage.text = message.text
                holder.binding.txtMeta.text = ChatMessage.clock(message.timestamp)
                holder.binding.txtMessage.maxWidth = maxWidth
            }
            is MineHolder -> {
                holder.binding.txtMessage.text = message.text
                holder.binding.txtMeta.text = ChatMessage.clock(message.timestamp)
                holder.binding.txtMessage.maxWidth = maxWidth
            }
        }

        holder.itemView.setOnLongClickListener {
            onLongPress(message)
            true
        }
        // Only rows seen for the first time get the fade-in, so scrolling and
        // full-list rebinds stay still.
        if (animated.add(message.id)) {
            holder.itemView.startAnimation(
                android.view.animation.AnimationUtils.loadAnimation(
                    holder.itemView.context,
                    R.anim.message_in
                )
            )
        }
    }

    class OtherHolder(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)
    class MineHolder(val binding: ItemMessageMineBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val TYPE_OTHER = 0
        const val TYPE_MINE = 1
        const val MAX_WIDTH_PCT = 0.78
    }
}
