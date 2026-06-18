package com.github.kr328.clash.design

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.adapter.ProxyAdapter
import com.github.kr328.clash.design.component.ProxyMenu
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.databinding.DesignProxyBinding
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.invalidateChildren
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.util.swapDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProxyDesign(
    context: Context,
    overrideMode: TunnelState.Mode?,
    private val groupNames: List<String>,
    private val uiStore: UiStore,
) : Design<ProxyDesign.Request>(context) {
    sealed class Request {
        object ReloadAll : Request()
        object ReLaunch : Request()

        data class PatchMode(val mode: TunnelState.Mode?) : Request()
        data class Reload(val index: Int) : Request()
        data class Select(val index: Int, val name: String) : Request()
        data class UrlTest(val index: Int) : Request()
    }

    private val binding = DesignProxyBinding
        .inflate(context.layoutInflater, context.root, false)

    private var config = ProxyViewConfig(context, uiStore.proxyLine)

    private val menu: ProxyMenu by lazy {
        ProxyMenu(context, binding.menuView, overrideMode, uiStore, requests) {
            config.proxyLine = uiStore.proxyLine
        }
    }

    private data class GroupSection(
        val header: TextView,
        val recyclerView: RecyclerView,
        val adapter: ProxyAdapter,
        var expanded: Boolean,
        var urlTesting: Boolean = false,
    )

    private val sections = mutableListOf<GroupSection>()
    private var activeGroupIndex = 0

    override val root: View = binding.root

    suspend fun updateGroup(
        position: Int,
        proxies: List<Proxy>,
        selectable: Boolean,
        parent: ProxyState,
        links: Map<String, ProxyState>
    ) {
        val states = withContext(Dispatchers.Default) {
            proxies.map {
                val link = if (it.type.group) links[it.name] else null

                com.github.kr328.clash.design.component.ProxyViewState(config, it, parent, link)
            }
        }

        withContext(Dispatchers.Main) {
            sections[position].apply {
                adapter.selectable = selectable
                adapter.swapDataSet(adapter::states, states, false)
                urlTesting = false
                recyclerView.invalidateChildren()
            }
            updateUrlTestButtonStatus()
        }
    }

    suspend fun requestRedrawVisible() {
        withContext(Dispatchers.Main) {
            sections.forEach {
                if (it.expanded) {
                    it.recyclerView.invalidateChildren()
                }
            }
        }
    }

    suspend fun showModeSwitchTips() {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.mode_switch_tips, Toast.LENGTH_LONG).show()
        }
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.menuView.setOnClickListener {
            menu.show()
        }

        if (groupNames.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE

            binding.urlTestView.visibility = View.GONE
            binding.urlTestFloatView.visibility = View.GONE
            binding.groupsScrollView.visibility = View.GONE
        } else {
            binding.urlTestFloatView.supportImageTintList = ColorStateList.valueOf(
                context.resolveThemedColor(com.google.android.material.R.attr.colorOnPrimary)
            )

            val initialPosition = groupNames.indexOf(uiStore.proxyLastGroup)
                .takeIf { it >= 0 }
                ?: 0

            groupNames.forEachIndexed { index, name ->
                addGroupSection(index, name, index == initialPosition)
            }

            activeGroupIndex = initialPosition
            updateUrlTestButtonStatus()
        }
    }

    fun requestUrlTesting() {
        sections.getOrNull(activeGroupIndex)?.urlTesting = true

        requests.trySend(Request.UrlTest(activeGroupIndex))

        updateUrlTestButtonStatus()
    }

    private fun addGroupSection(index: Int, name: String, expanded: Boolean) {
        val header = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                52.dp
            ).apply {
                topMargin = 8.dp
                bottomMargin = 4.dp
            }
            background = ContextCompat.getDrawable(context, R.drawable.bg_sxy_action)
            setPadding(18.dp, 0, 18.dp, 0)
            gravity = android.view.Gravity.CENTER_VERTICAL
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val adapter = ProxyAdapter(config) { proxyName ->
            activeGroupIndex = index
            requests.trySend(Request.Select(index, proxyName))
        }

        val recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8.dp
            }
            layoutManager = GridLayoutManager(context, 2)
            isNestedScrollingEnabled = false
            clipToPadding = false
            this.adapter = adapter
            visibility = if (expanded) View.VISIBLE else View.GONE
        }

        val section = GroupSection(header, recyclerView, adapter, expanded)
        sections += section

        header.setOnClickListener {
            activeGroupIndex = index
            section.expanded = !section.expanded
            recyclerView.visibility = if (section.expanded) View.VISIBLE else View.GONE
            refreshGroupHeaders()
            uiStore.proxyLastGroup = name
            updateUrlTestButtonStatus()
        }

        binding.groupsContainer.addView(header)
        binding.groupsContainer.addView(recyclerView)
        refreshGroupHeaders()
    }

    private fun refreshGroupHeaders() {
        sections.forEachIndexed { index, section ->
            val marker = if (section.expanded) "v" else ">"
            section.header.text = "$marker  ${groupNames[index]}"
        }
    }

    private fun updateUrlTestButtonStatus() {
        val testing = sections.getOrNull(activeGroupIndex)?.urlTesting == true

        if (testing) {
            binding.urlTestFloatView.hide()
        } else {
            binding.urlTestFloatView.show()
        }

        if (testing) {
            binding.urlTestView.visibility = View.GONE
            binding.urlTestProgressView.visibility = View.VISIBLE
        } else {
            binding.urlTestView.visibility = View.VISIBLE
            binding.urlTestProgressView.visibility = View.GONE
        }
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()
}
