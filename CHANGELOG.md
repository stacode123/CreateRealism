## 0.6.7
### Fixed
- Fixed a dedicated server crash caused by the ETCS speedometer reading the client config during the train tick
- The ETCS speedometer needle now follows the client-side km/h / mph setting
- Fixed time-of-day departure gates drifting when the server runs an accelerated or slowed clock: timetable slots are now tracked in day time instead of game ticks, so trains depart on their published clock times again
