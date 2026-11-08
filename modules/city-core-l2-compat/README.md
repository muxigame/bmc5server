# City Core L2 registry stream compatibility

Both client and server must install this addon with locked L2Library3.0.8/L2Serial3.1.1. It does not modify upstream jars, content, authentication or saved data.

The original library caches a registry-sync codec decision during static initialization. Actual dedicated/client observations differed: named Item36 bytes vs indexed Item2 bytes. The real1324-byte server config reproduced the client StatFilterType failure. This addon selects the original named/indexed stream when encode/decode actually occurs, after registry initialization.

The exact candidate class/resource contents passed an actual NeoForge server config encode/roundtrip and actual client title-screen memory replay: both Item2 bytes; real1072-byte config;8 filter entries,3 magnifier entries,zero unread bytes. This is a wire replay regression, not real-login, registry-sync, all6-registry semantic identity, YSM render or complete city-loop acceptance. Missing server-data/map-assets remains unresolved. No stable release is claimed.

Build using existing Java21 and a mapped NeoForge1.21.1/L2Serial3.1.1 compilation classpath:

`python -B build.py --javac /path/to/javac --classpath-file /path/to/classpath.txt --out /path/to/city-core-l2-compat-0.0.2.jar`

The classpath file contains one UTF8 semicolon-separated classpath. Dependencies are reused read-only. The output is deterministic with fixed ZIP metadata; existing files are never overwritten. No server or client starts during build. Source is MIT; upstream libraries retain their own licenses.
