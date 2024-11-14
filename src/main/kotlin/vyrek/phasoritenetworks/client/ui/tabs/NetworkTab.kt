package vyrek.phasoritenetworks.client.ui.tabs

import io.wispforest.owo.ui.component.BoxComponent
import io.wispforest.owo.ui.component.ColorPickerComponent
import io.wispforest.owo.ui.container.CollapsibleContainer
import io.wispforest.owo.ui.container.FlowLayout
import io.wispforest.owo.ui.core.Color
import vyrek.phasoritenetworks.client.ui.*
import vyrek.phasoritenetworks.common.networks.NetworkUserAccess
import vyrek.phasoritenetworks.common.searchBy
import vyrek.phasoritenetworks.networking.PNEndecsData
import vyrek.phasoritenetworks.networking.PutType
import kotlin.uuid.Uuid

enum class NetworkSortMethod {
	All,
	Owned
}

class NetworkTab(screen: UIScreen) : BaseScrollTab<NetworkSortMethod, PNEndecsData.ClientNetworkData>(screen) {
	override var height = 202
	override var sortedBy = NetworkSortMethod.All
	override val scrollData = menu.accessibleNetworks.filter { it.id != menu.network?.id }

	override fun build(root: FlowLayout) {
		super.build(root)

		val create = root.button("button:network-create").onPress {
			if (!activeTab.isNetworkSubtab()) buildFormTab(root, Tabs.NETWORK_CREATE)
		}
		val disconnect = root.button("button:network-disconnect").onPress {
			if (menu.network == null) return@onPress

			menu.disconnectNetwork()
		}
		val edit = root.button("button:network-edit")
		val delete = root.button("button:network-delete").onPress {
			if (menu.network == null) return@onPress

			buildDeleteTab(root)
		}
		val container = root.flowLayout("flow-layout:container-buttons").apply {
			removeChild(delete)
			removeChild(edit)
			removeChild(disconnect)
		}

		val network = menu.network
		if (!isBlockOwner()) {
			container.removeChild(create)
			return
		}
		if (network == null) return

		container.removeChild(create)

		container.children(
			mutableListOf(
				delete,
				edit.onPress {
					buildFormTab(root, Tabs.NETWORK_EDIT)
				},
				disconnect,
				create
			).apply {
				if (menu.player.uuid != network.owner) removeFirst()
				if (menu.player.uuid != network.owner &&
					network.members[menu.player.uuid]?.access != NetworkUserAccess.ADMIN.ordinal
				) removeFirst()
			}
		)
	}

	override fun filterData(): List<PNEndecsData.ClientNetworkData> {
		val filteredNetworks = when (sortedBy) {
			NetworkSortMethod.All -> scrollData
			NetworkSortMethod.Owned -> scrollData.filter { it.owner == menu.player.uuid }
		}

		return searchBy?.let { filteredNetworks.searchBy(it) { data -> data.name } } ?: filteredNetworks
	}

	override fun buildScrollData(i: Int, data: PNEndecsData.ClientNetworkData) {
		scroll.child(
			screen.uiModel.expandTemplate(
				FlowLayout::class, "tab:network:data", mutableMapOf(
					"id" to data.id.toString(),
					"name" to data.name,
					"color" to Color.ofArgb(data.color).asHexString(true)
				)
			).apply {
				mouseDown().subscribe { _, _, _ ->
					if (!isBlockOwner()) return@subscribe true

					if (data.password != "") buildPasswordTab(screen.rootComponent, data)
					else menu.connectNetwork(data.id, "")

					true
				}
			}
		)
	}

	private fun buildFormTab(component: FlowLayout, tab: Tabs) {
		activeTab = tab

		val network = menu.network.takeIf { tab != Tabs.NETWORK_CREATE }

		component.child(
			screen.uiModel.expandTemplate(FlowLayout::class, "background:vanilla-translucent").apply {
				flowLayout("flow-layout:container-background")
					.child(
						screen.uiModel.expandTemplate(
							FlowLayout::class, "tab:network:form", screen.generateTemplateParams(tab)
						)
					)
			}
		)

		component.button("button:close").onPress {
			screen.buildTab(component, Tabs.NETWORK, ::NetworkTab)
		}

		val name = component.textArea("text-area:network-name").apply {
			onChanged().subscribe { t ->
				if (t.length > 4) clearError(component)
			}
		}

		val passwordContainer = component.flowLayout("flow-layout:container-password")
		val collapsible = component.childById(CollapsibleContainer::class, "collapsible:container-password").apply {
			onToggled().subscribe { toggle ->
				if (network != null && network.password != "" && toggle) {
					component.textArea("text-area:network-password").apply {
						text(network.password)
					}
				}
			}
		}

		val passwordContainerChildren = passwordContainer.children().toList()
		if (network == null || network.private) passwordContainer.clearChildren()

		component.checkbox("checkbox:network-private").onChanged { value ->
			if (!value) passwordContainer.children(passwordContainerChildren)
			else passwordContainer.clearChildren()
		}

		val colorPicker = component.childById(ColorPickerComponent::class, "color-picker:network-color").apply {
			onChanged().subscribe { color ->
				component.childById(BoxComponent::class, "box:network-color").color(color).fill(true)
			}
		}
		if (network != null) colorPicker.selectedColor(Color.ofArgb(network.color))

		component.button("button:form-type").onPress {
			if (name.value.length <= 4) {
				displayError(component, "Name shouldn't be less or equal than 4.")
				return@onPress
			}

			if (!collapsible.expanded()) collapsible.toggleExpansion()

			val private = component.checkbox("checkbox:network-private")
			val pwd = if (private.selected()) "" else component.textArea("text-area:network-password").value

			menu.putNetwork(
				if (activeTab == Tabs.NETWORK_CREATE) PutType.CREATE else PutType.UPDATE,
				name.value,
				colorPicker.selectedColor(),
				private.selected(),
				pwd,
				id = if (network?.id != null) network.id else Uuid.NIL
			)

			screen.buildTab(component, Tabs.NETWORK, ::NetworkTab)
		}
	}

	private fun buildDeleteTab(component: FlowLayout) {
		activeTab = Tabs.NETWORK_DELETE

		component.child(
			popupWithBackground(
				"background:vanilla-translucent",
				screen.uiModel.expandTemplate(
					FlowLayout::class, "tab:network:delete", mutableMapOf("name" to menu.network!!.name)
				)
			)
		)

		component.button("button:close").onPress {
			screen.buildTab(component, Tabs.NETWORK, ::NetworkTab)
		}

		component.button("button:network-delete").onPress {
			menu.deleteNetwork()
		}
	}

	private fun buildPasswordTab(component: FlowLayout, data: PNEndecsData.ClientNetworkData) {
		activeTab = Tabs.NETWORK_PASSWORD
		var tries = 0

		component.child(
			popupWithBackground(
				"background:vanilla-translucent",
				screen.uiModel.expandTemplate(FlowLayout::class, "tab:network:password")
			)
		)

		component.button("button:close").onPress {
			screen.buildTab(component, Tabs.NETWORK, ::NetworkTab)
		}

		val pwd = component.textArea("text-area:network-password")

		component.button("button:network-enter").onPress {
			if (tries >= 3) return@onPress screen.buildTab(component, Tabs.NETWORK, ::NetworkTab)

			if (pwd.value != data.password) {
				tries++
				return@onPress displayError(
					component,
					"Incorrect password. Please try again. You have ${3 - tries} attempts left."
				)
			}

			screen.buildTab(component, Tabs.NETWORK, ::NetworkTab)

			menu.connectNetwork(data.id, pwd.value)
		}
	}
}