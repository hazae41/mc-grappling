package hazae41.minecraft.grappling

import hazae41.minecraft.kotlin.bukkit.BukkitPlugin
import hazae41.minecraft.kotlin.bukkit.PluginConfigFile
import hazae41.minecraft.kotlin.bukkit.command
import hazae41.minecraft.kotlin.bukkit.info
import hazae41.minecraft.kotlin.bukkit.init
import hazae41.minecraft.kotlin.bukkit.listen
import hazae41.minecraft.kotlin.bukkit.msg
import hazae41.minecraft.kotlin.bukkit.update
import hazae41.minecraft.kotlin.catch
import hazae41.minecraft.kotlin.ex
import org.bukkit.Location
import org.bukkit.Material.FISHING_ROD
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment.DURABILITY
import org.bukkit.enchantments.Enchantment.RIPTIDE
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerFishEvent.State.FAILED_ATTEMPT
import org.bukkit.event.player.PlayerFishEvent.State.IN_GROUND
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType.DOUBLE
import java.lang.Integer.parseInt
import kotlin.math.roundToInt

object Config : PluginConfigFile("config") {
    val debug by boolean("debug")
    val usePermission by string("use-permission")
    val givePermission by string("give-permission")
    val name by string("name")
    val lore by stringList("lore")
    val force by double("force")
    val durability by int("durability")
}

class Plugin : BukkitPlugin() {
    override fun onEnable() {
        update(71059)
        init(Config)
        registerEvents()
        registerCommands()
    }
}

val ItemMeta.db get() = persistentDataContainer
val Plugin.usesKey get() = NamespacedKey(this, "uses")

fun isGrappling(item: ItemStack) = item.getEnchantmentLevel(RIPTIDE) > 0

fun Plugin.makeGrappling(force: Int, durability: Int) = ItemStack(FISHING_ROD, 1).apply {
    addUnsafeEnchantment(RIPTIDE, force)
    if (durability > 0) addUnsafeEnchantment(DURABILITY, durability)
    itemMeta = itemMeta?.apply {
        setDisplayName(Config.name)
        lore = Config.lore
    }
}

fun Player.pull(to: Location, force: Int) {
    val location = location
    val vector = to.toVector().subtract(location.toVector()).normalize()
    velocity = vector.multiply(Config.force).multiply(force)
}

fun Plugin.debug(ex: java.lang.Exception) {
    if (Config.debug) info(ex)
}

fun Plugin.registerEvents() {
    listen<PlayerFishEvent> {
        catch(::debug) {
            val nostate = ex("Wrong state")
            if (it.state !in listOf(IN_GROUND, FAILED_ATTEMPT)) throw nostate

            val item = it.player.inventory.itemInMainHand
            val noitem = ex("Wrong item")
            if (!isGrappling(item)) throw noitem

            if (Config.usePermission.isNotBlank()) {
                val noperm = ex("No permission")
                if (!it.player.hasPermission(Config.usePermission)) throw noperm
            }

            it.isCancelled = true

            val force = item.getEnchantmentLevel(RIPTIDE)
            it.player.pull(it.hook.location, force)

            val durability = item.getEnchantmentLevel(DURABILITY)
            val max = item.type.maxDurability

            item.itemMeta = item.itemMeta!!.apply {
                val uses = run {
                    if (!db.has(usesKey, DOUBLE)) 0.0
                    else db.get(usesKey, DOUBLE)!!
                }.plus(1.0 / (durability + 1))
                (this as Damageable).damage = (uses * max / Config.durability).roundToInt()
                if (uses >= Config.durability) item.amount = 0
                db.set(usesKey, DOUBLE, uses)
            }
            it.player.updateInventory()
        }

    }
}

fun Plugin.registerCommands() {
    command("grappling") { args ->
        catch<Exception>(::msg) {
            val help = ex("/grappling give <player> [force] [durability]")
            when (args.getOrNull(0)) {
                "give" -> if (hasPermission(Config.givePermission)) {
                    val name = args.getOrNull(1) ?: throw help
                    val unknown = ex("Unknown player")
                    val player = server.matchPlayer(name).getOrNull(0) ?: throw unknown
                    val force = args.getOrNull(2)?.let { parseInt(it) } ?: 1
                    val durability = args.getOrNull(3)?.let { parseInt(it) } ?: 0
                    val item = makeGrappling(force, durability)
                    player.inventory.addItem(item)
                    msg("Done")
                }
                else -> throw help
            }
        }

    }
}