PLUGINS = [
    "netbox_topology_views",
    "netbox_documents",
]

PLUGINS_CONFIG = {
    "netbox_topology_views": {
        "allow_coordinates_saving": True,
        "always_save_coordinates": True,
    },
    "netbox_documents": {
        "enable_navigation_menu": True,
        "enable_device_documents": True,
        "enable_device_type_documents": True,
    },
}
