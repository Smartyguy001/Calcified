package dev.frozenmilk.dairy.calcified

import android.content.Context
import com.qualcomm.hardware.lynx.LynxModule
import com.qualcomm.robotcore.hardware.Gamepad
import com.qualcomm.robotcore.hardware.configuration.LynxConstants
import dev.frozenmilk.dairy.calcified.gamepad.CalcifiedGamepad
import dev.frozenmilk.dairy.calcified.hardware.CalcifiedModule
import dev.frozenmilk.dairy.core.DairyCore
import dev.frozenmilk.dairy.core.Feature
import dev.frozenmilk.dairy.core.FeatureRegistrar
import dev.frozenmilk.dairy.core.OpModeWrapper
import dev.frozenmilk.dairy.core.dependencyresolution.dependencyset.DependencySet
import dev.frozenmilk.util.cell.LateInitCell
import dev.frozenmilk.util.cell.LazyCell
import org.firstinspires.ftc.ftccommon.external.OnCreate
import org.firstinspires.ftc.robotcore.internal.opmode.OpModeMeta
import org.firstinspires.ftc.robotcore.internal.opmode.OpModeMeta.Flavor
import java.lang.annotation.Inherited

/**
 * enabled by having either @[DairyCore] or @[Calcify]
 */
object Calcified : Feature {
	/**
	 * @see Attach.crossPollinate
	 */
	@JvmStatic
	var crossPollinate = true
		private set

	/**
	 * @see Attach.automatedCacheHandling
	 */
	@JvmStatic
	var automatedCacheHandling = true
		private set

	/**
	 * enabled by having either @[DairyCore] or @[Attach]
	 */
	override val dependencies = DependencySet(this)
			.includesExactlyOneOf(DairyCore::class.java, Attach::class.java).bindOutputTo {
				when (it) {
					is Attach -> {
						crossPollinate = it.crossPollinate
						automatedCacheHandling = it.automatedCacheHandling
					}

					else -> {
						crossPollinate = true
						automatedCacheHandling = true
					}
				}
			}


	/**
	 * does not need to be called by the user,
	 *
	 * todo test this as a method of self registration
	 */
	@OnCreate
	@JvmStatic
	fun registerFeature(@Suppress("UNUSED_PARAMETER") context: Context) {
		FeatureRegistrar.registerFeature(this)
	}

	/**
	 * all calcified modules found this OpMode
	 */
	@JvmStatic
	var modules: Array<CalcifiedModule> = emptyArray()
		private set

	private var boringGamepad1: Gamepad by LateInitCell()
	private var boringGamepad2: Gamepad by LateInitCell()

	private val gamepad1Cell = LazyCell { CalcifiedGamepad(boringGamepad1) }
	@JvmStatic
	val gamepad1: CalcifiedGamepad by gamepad1Cell
	private val gamepad2Cell = LazyCell { CalcifiedGamepad(boringGamepad2) }
	@JvmStatic
	val gamepad2: CalcifiedGamepad by gamepad2Cell

	private val controlHubCell = LazyCell {
		if (!FeatureRegistrar.opModeActive) throw IllegalStateException("OpMode not inited, cannot yet access the control hub")
		modules.filter { it.lynxModule.isParent && LynxConstants.isEmbeddedSerialNumber(it.lynxModule.serialNumber) }.getOrNull(0) ?:throw IllegalStateException(("The control hub was not found, this may be an electronics issue"))
	}

	/**
	 * the first hub in [modules] that satisfies the conditions to be considered a control hub
	 */
	@JvmStatic
	val controlHub: CalcifiedModule by controlHubCell

	private val expansionHubCell = LazyCell {
		if (!FeatureRegistrar.opModeActive) throw IllegalStateException("OpMode not inited, cannot yet access the expansion hub")
		modules.filter { !(it.lynxModule.isParent && LynxConstants.isEmbeddedSerialNumber(it.lynxModule.serialNumber)) }.getOrNull(0) ?: throw IllegalStateException(("The expansion hub was not found, this may be an electronics issue"))
	}

	/**
	 * the first hub in [modules] that satisfies the conditions to be considered an expansion hub
	 */
	@JvmStatic
	val expansionHub: CalcifiedModule by expansionHubCell

	/**
	 * internal refresh caches, only refreshes if the automated process is enabled
	 */
	private fun refreshCaches() {
		if (automatedCacheHandling) modules.forEach { it.refreshBulkCache() }
	}

	/**
	 * should be run in stop if you want to clear the status of the hardware objects for the next user, otherwise modules and hardware will be cleared according to [crossPollinate]
	 */
	@JvmStatic
	fun clearModules() {
		modules = emptyArray()
	}

	override fun preUserInitHook(opMode: OpModeWrapper) {
		// if cross pollination is enabled, and the OpMode type is Teleop, then we want to keep our pre-existing modules and hubs
		// however, if we have no modules (like after a teleop or at the very start) then we want to find new modules too
		// if cross pollination is disabled, we only want to find new stuff if the modules are empty
		if(modules.isEmpty() || (crossPollinate && when(opMode.opModeType) {
					Flavor.TELEOP -> false
					Flavor.AUTONOMOUS -> true
					Flavor.SYSTEM -> false
				})) {
			modules = opMode.hardwareMap.getAll(LynxModule::class.java).map {
				CalcifiedModule(it)
			}.toTypedArray()

			controlHubCell.invalidate()
			expansionHubCell.invalidate()
		}

		boringGamepad1 = opMode.gamepad1
		boringGamepad2 = opMode.gamepad2

		gamepad1Cell.invalidate()
		gamepad2Cell.invalidate()

		refreshCaches()
	}

	override fun postUserInitHook(opMode: OpModeWrapper) {
	}

	override fun preUserInitLoopHook(opMode: OpModeWrapper) {
		refreshCaches()
	}

	override fun postUserInitLoopHook(opMode: OpModeWrapper) {
	}

	override fun preUserStartHook(opMode: OpModeWrapper) {
		refreshCaches()
	}


	override fun postUserStartHook(opMode: OpModeWrapper) {
	}

	override fun preUserLoopHook(opMode: OpModeWrapper) {
		refreshCaches()
	}

	override fun postUserLoopHook(opMode: OpModeWrapper) {
	}

	override fun preUserStopHook(opMode: OpModeWrapper) {
		refreshCaches()
	}

	override fun postUserStopHook(opMode: OpModeWrapper) {
		if (crossPollinate && opMode.opModeType == Flavor.TELEOP) {
			clearModules()
		}
	}

	@Retention(AnnotationRetention.RUNTIME)
	@Target(AnnotationTarget.CLASS)
	@MustBeDocumented
	@Inherited
	annotation class Attach(
			/**
			 * Controls if the caches are automatically handled by [Calcified] or not
			 *
			 * Set to false if you want to handle the clearing of the module caches by hand
			 *
			 * Clearing the caches should probably be done using [CalcifiedModule.refreshBulkCache]
			 */
			val automatedCacheHandling: Boolean = true,
			/**
			 * Controls when [Calcified] drops its modules and this hardware objects
			 *
			 * Set to false if you want to prompt [Calcified] to drop its hardware objects by hand.
			 * This should be done using [Calcified.clearModules]
			 *
			 * By default, drops hardware objects at the start of an auto, and at the end of a teleop, allowing values to be carried over from an auto to a teleop
			 */
			val crossPollinate: Boolean = true
	)
}
