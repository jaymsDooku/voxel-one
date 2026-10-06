# Jeep

Offline games park one open-top voxel 4x4 jeep near the initial spawn. Its windshield is transparent glass. The body has four wheels, two seats, bumpers, a grille and headlights. The parked location and heading persist beside the world save in its `.jeep` file.

Walk within 4 blocks of the jeep and press J to enter the driver's seat. Use W to drive forward and S to reverse. A/D and horizontal mouse motion turn the jeep. Hold either Ctrl key with W or S for faster acceleration and a higher speed limit. Release W/S to brake. Stop and press J to exit. Leave clear ground beside the vehicle for a safe exit. An exit while moving, in the air or with both sides blocked is rejected.

F5 cycles the camera while driving. The first-person view looks through the glass windshield. Inventory, menus and released mouse capture stop throttle input. Flight and block editing are disabled while seated. Respawn releases the driver's seat and stops the jeep.

Movement uses the existing walking bindings. The J entry/exit action can be changed in the controls menu. The HUD shows the active entry/exit key and speed in km/h.

The full body collides with terrain, glass blocks and unloaded world edges. A clear, supported path can climb one-block steps. The jeep cannot pass through deep water or tall walls. Its conservative collision box can stop a turn near tight corners.

This implementation supports offline play. Multiplayer vehicle authority and synchronization are not included.
