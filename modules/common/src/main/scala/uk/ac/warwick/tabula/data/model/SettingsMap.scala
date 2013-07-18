package uk.ac.warwick.tabula.data.model

import org.hibernate.annotations.Type
import javax.persistence._

trait HasSettings {

	@Type(`type` = "uk.ac.warwick.tabula.data.model.JsonMapUserType")
	protected var settings: Map[String, Any] = Map()

	protected def settingsIterator = settings.iterator

	protected def getSetting(key: String) = settings.get(key)

	protected def getStringSetting(key: String) = settings.get(key) match {
		case Some(value: String) => Some(value)
		case _ => None
	}
	protected def getIntSetting(key: String) = settings.get(key) match {
		case Some(value: Int) => Some(value)
		case _ => None
	}
	protected def getBooleanSetting(key: String) = settings.get(key) match {
		case Some(value: Boolean) => Some(value)
		case _ => None
	}
	protected def getStringSeqSetting(key: String) = settings.get(key) match {
		case Some(value: Seq[_]) => Some(value.asInstanceOf[Seq[String]])
		case _ => None
	}

	protected def getStringSetting(key: String, default: => String): String = getStringSetting(key) getOrElse(default)
	protected def getIntSetting(key: String, default: => Int): Int = getIntSetting(key) getOrElse(default)
	protected def getBooleanSetting(key: String, default: => Boolean): Boolean = getBooleanSetting(key) getOrElse(default)
	protected def getStringSeqSetting(key: String, default: => Seq[String]): Seq[String] = getStringSeqSetting(key) getOrElse(default)

	protected def settingsSeq = settings.toSeq

	protected def ensureSettings {
		if (settings == null) settings = Map()
	}
}

/**
 * Danger!
 *
 * Exposing the ++= methods publicly means that other classes can directly manipulate the keys of this map.
 * If your class expects keys to have certain specified values (e.g. enums) then mix in HasSettings and
 * expose type-safe getters and setters instead of mixing in SettingsMap directly
 */

trait SettingsMap[A <: SettingsMap[A]] extends HasSettings { self: A =>
	

	protected def -=(key: String) = {
		settings -= key
		self
	}
	
	protected def +=(kv: (String, Any)) = {
		settings += kv
		self
	}
	
	def ++=(sets: Pair[String, Any]*) = {
		settings ++= sets
		self
	}

	def ++=(other: A) = {
		settings ++= other.settings
		self
	}
}