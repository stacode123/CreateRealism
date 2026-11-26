## 0.7
### Added
**Overhauled ETCS**:
- You can now open the ETCS window with a keybind (default: V)
- **Route Guidance** - You can now set a destination and ETCS will steer the train for you. This allows you to travel with trains manually on complicated networks where timing turns and checking routing is cumbersome. Just input the station and hold "W"
- Route Guidance now reserves signal blocks within the train's stopping distance, so other trains wait instead of entering the block ahead. Can be turned off in the server config.
- Train settings in the ETCS window  You can now change train settings in the ETCS window.



### Fixed
- Fixed a dedicated server crash caused by the ETCS speedometer reading the client config during the train tick
- The ETCS speedometer needle now follows the client-side km/h / mph setting
- Fixed time-of-day departure gates drifting when the server runs an accelerated or slowed clock: timetable slots are now tracked in day time instead of game ticks, so trains depart on their published clock times again
- Improved "Time of day Realistic" reliability 
- Fixed Tramways related crash
- Cleaned up ETCS textures
