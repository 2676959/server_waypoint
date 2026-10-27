# System build reference

Captured on October 27, 2026, from the current performance workstation and the
configured Paper 1.21.11 Java executable. This is a current reference snapshot,
not a reconstruction of the environment used for earlier measurements.

| Property | Recorded value |
| --- | --- |
| Machine model | Mac16,10 |
| CPU | Apple M4 |
| Architecture | arm64 |
| Physical cores / logical CPUs | 10 / 10 |
| Memory | 16 GiB (17,179,869,184 bytes) |
| OS version | macOS 26.5 |
| OS build | 25F71 |
| Kernel release | Darwin 25.5.0 |
| Java version | OpenJDK 21.0.11, April 21, 2026 |
| Java runtime build | Homebrew 21.0.11 |
| Java VM | OpenJDK 64-Bit Server VM, mixed mode, sharing |
| Suite JVM flags | `-Xms1G -Xmx2G -XX:+UseG1GC` |
| Load generator | Same host; client count declared per case |
| OS-reported maximum CPU frequency | Unavailable |

Hardware/OS values were queried with `sysctl`, `sw_vers` and `uname`. Java build
details came from the configured runtime's `java -version`; JVM flags and driver
placement describe the suite configuration, not a newly executed workload.

CPU frequency is managed by the OS; the suite applies no clock-speed cap. Actual
frequency and thermal throttling during a workload are not sampled. Timing results
on this host do not establish equivalent timing on slower or different hardware.

New prepared runs seal their own `environment.json` with the input evidence.
Generated `report.md` includes that run's system reference and Java builds;
`report.json` embeds the same snapshot. Missing values are marked unavailable,
and a report without a snapshot says that system information was not recorded.
