extends Node
class_name InAppUpdatePluginInterfacer

signal update_found(is_urgent: bool)
signal update_ready
signal update_failed(error_message: String, error_code: int)
signal update_download_status_updated(data_downloaded_ratio: float)

var update_is_ready : bool:
	get:
		return _update_is_ready

var _in_app_update_singleton
var _update_is_ready : bool = false

func _init() -> void:
	if not Engine.has_singleton("GodotAndroidInAppUpdate"):
		printerr("Couldn't find GodotAndroidInAppUpdate singleton")
		return
	
	_in_app_update_singleton = Engine.get_singleton("GodotAndroidInAppUpdate")
	_connect_signals()

func check_for_updates() -> void:
	if not _has_singleton():
		return
	
	_in_app_update_singleton.checkForUpdate()

func start_immediate_update() -> void:
	if not _has_singleton():
		return
	
	_update_is_ready = false
	_in_app_update_singleton.setImmediateUpdate()

func start_flexible_update() -> void:
	if not _has_singleton():
		return
	
	_update_is_ready = false
	_in_app_update_singleton.setFlexibleUpdate()

func complete_update_installation() -> void:
	if not _has_singleton():
		return
	
	if _update_is_ready:
		_update_is_ready = false
		_in_app_update_singleton.installUpdate()

func _connect_signals() -> void:
	if not _has_singleton():
		return
	
	_in_app_update_singleton.connect("updateFound", update_found.emit)
	_in_app_update_singleton.connect("updateReady", update_ready.emit)
	_in_app_update_singleton.connect("updateFailed", update_failed.emit)
	_in_app_update_singleton.connect("updateDownloadStatus", update_download_status_updated.emit)
	
	update_ready.connect(_on_update_ready)

func _has_singleton() -> bool:
	if _in_app_update_singleton:
		return true
	else:
		printerr("In App Update Singleton hasn't been created")
		return false

func _on_update_ready() -> void:
	_update_is_ready = true
