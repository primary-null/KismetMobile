package com.kismet.mobile.sdr

data class KaliTool(
    val name: String,
    val category: String,
    val description: String,
    val installCmd: String,
    val runCmd: String,
    val kismetSource: String = "",
)

object KaliSdrTools {

    val tools = listOf(

        // ── Kismet native sources ────────────────────────────────────────────
        KaliTool(
            name = "hackrf-sweep (Kismet)",
            category = "KISMET SOURCE",
            description = "Broadband spectrum sweep via HackRF. Kismet native source.",
            installCmd = "sudo apt install -y kismet",
            runCmd = "kismet -c hackrf-sweep-source",
            kismetSource = "source=hackrf-sweep-source:name=hackrf-sweep",
        ),
        KaliTool(
            name = "RTL-ADSB (Kismet)",
            category = "KISMET SOURCE",
            description = "ADS-B aircraft tracking via RTL-SDR/HackRF. Kismet plugin.",
            installCmd = "sudo apt install -y kismet-capture-sdr-rtladsb",
            runCmd = "kismet_cap_sdr_rtladsb --connect localhost:3501 --source rtladsb-0",
            kismetSource = "source=rtladsb-0:name=adsb",
        ),
        KaliTool(
            name = "RTL-433 (Kismet)",
            category = "KISMET SOURCE",
            description = "433 MHz sensors — weather stations, IoT, garage doors.",
            installCmd = "sudo apt install -y kismet-capture-sdr-rtl433",
            runCmd = "kismet_cap_sdr_rtl433 --connect localhost:3501 --source rtl433-0",
            kismetSource = "source=rtl433-0:name=rtl433",
        ),
        KaliTool(
            name = "RTL-AMR (Kismet)",
            category = "KISMET SOURCE",
            description = "Smart meter / utility meter traffic (AMR/ERT protocol).",
            installCmd = "sudo apt install -y kismet-capture-sdr-rtlamr",
            runCmd = "kismet_cap_sdr_rtlamr --connect localhost:3501 --source rtlamr-0",
            kismetSource = "source=rtlamr-0:name=amr",
        ),

        // ── Ubertooth ────────────────────────────────────────────────────────
        KaliTool(
            name = "Ubertooth One (Kismet)",
            category = "KISMET SOURCE",
            description = "Ubertooth One BTLE + classic BT promiscuous capture. Kismet native source.",
            installCmd = "sudo apt install -y ubertooth kismet-capture-ubertooth-one",
            runCmd = "kismet -c ubertooth-0",
            kismetSource = "source=ubertooth-0:name=ubertooth",
        ),

        // ── RTL-TCP bridge ───────────────────────────────────────────────────
        KaliTool(
            name = "rtl_tcp",
            category = "TCP BRIDGE",
            description = "RTL-SDR IQ stream over TCP. Android SDR apps connect to this directly.",
            installCmd = "sudo apt install -y rtl-sdr",
            runCmd = "rtl_tcp -a 0.0.0.0 -p 1234 -s 2400000",
            kismetSource = "source=rtl-tcp:host=127.0.0.1,port=1234,name=rtl_tcp",
        ),

        // ── ADS-B ────────────────────────────────────────────────────────────
        KaliTool(
            name = "dump1090",
            category = "ADS-B",
            description = "ADS-B decoder. Feeds aircraft data into Kismet or map viewers.",
            installCmd = "sudo apt install -y dump1090-fa",
            runCmd = "dump1090 --interactive --net --net-ro-port 30002",
            kismetSource = "source=rtladsb-0",
        ),

        // ── Spectrum / signal analysis ───────────────────────────────────────
        KaliTool(
            name = "hackrf_sweep",
            category = "SPECTRUM",
            description = "Broadband power spectrum sweep. Pipe output to files or tools.",
            installCmd = "sudo apt install -y hackrf",
            runCmd = "hackrf_sweep -f 80:6000 -w 100000 -l 32 -g 40",
            kismetSource = "",
        ),
        KaliTool(
            name = "rtl_power",
            category = "SPECTRUM",
            description = "RTL-SDR wideband power scan. CSV output compatible with heatmap tools.",
            installCmd = "sudo apt install -y rtl-sdr",
            runCmd = "rtl_power -f 80M:1G:1M -i 10 -e 1h out.csv",
            kismetSource = "",
        ),
        KaliTool(
            name = "gqrx",
            category = "SPECTRUM",
            description = "GUI SDR receiver with waterfall. Demodulates FM, AM, SSB, CW.",
            installCmd = "sudo apt install -y gqrx-sdr",
            runCmd = "gqrx",
            kismetSource = "",
        ),

        // ── Protocol analysis ────────────────────────────────────────────────
        KaliTool(
            name = "GNU Radio",
            category = "SIGNAL",
            description = "Full SDR processing framework. Custom flowgraphs pipe into Kismet.",
            installCmd = "sudo apt install -y gnuradio gr-osmosdr",
            runCmd = "gnuradio-companion",
            kismetSource = "",
        ),
        KaliTool(
            name = "Universal Radio Hacker",
            category = "SIGNAL",
            description = "Reverse engineer unknown RF protocols. Record, decode, attack.",
            installCmd = "sudo apt install -y urh",
            runCmd = "urh",
            kismetSource = "",
        ),
        KaliTool(
            name = "inspectrum",
            category = "SIGNAL",
            description = "IQ file analysis — view captured signals as time/frequency.",
            installCmd = "sudo apt install -y inspectrum",
            runCmd = "inspectrum capture.cf32",
            kismetSource = "",
        ),
        KaliTool(
            name = "multimon-ng",
            category = "SIGNAL",
            description = "Decode POCSAG, FLEX, DTMF, ZVEI, EAS, AX.25 from audio pipe.",
            installCmd = "sudo apt install -y multimon-ng",
            runCmd = "rtl_fm -f 154.4M -M fm -s 22050 - | multimon-ng -t raw -a POCSAG512 /dev/stdin",
            kismetSource = "",
        ),

        // ── Bluetooth ────────────────────────────────────────────────────────
        KaliTool(
            name = "ubertooth-btle",
            category = "BLUETOOTH",
            description = "Promiscuous BTLE capture to PCAP. Sniffs advertising + data channels.",
            installCmd = "sudo apt install -y ubertooth",
            runCmd = "ubertooth-btle -f -c capture.pcap",
            kismetSource = "source=ubertooth-0:name=ubertooth",
        ),
        KaliTool(
            name = "ubertooth-rx",
            category = "BLUETOOTH",
            description = "Classic Bluetooth BR/EDR promiscuous sniff. Hop-following after LAP discovery.",
            installCmd = "sudo apt install -y ubertooth",
            runCmd = "ubertooth-rx -l capture.pcap",
            kismetSource = "",
        ),
        KaliTool(
            name = "ubertooth-util",
            category = "BLUETOOTH",
            description = "Ubertooth device info, firmware flash, reset.",
            installCmd = "sudo apt install -y ubertooth",
            runCmd = "ubertooth-util -v",
            kismetSource = "",
        ),

        // ── Capture / recording ──────────────────────────────────────────────
        KaliTool(
            name = "hackrf_transfer",
            category = "CAPTURE",
            description = "Raw IQ capture to file. Replay attacks or offline analysis.",
            installCmd = "sudo apt install -y hackrf",
            runCmd = "hackrf_transfer -r capture.bin -f 433920000 -s 2000000 -l 32 -g 40",
            kismetSource = "",
        ),
    )

    val categories get() = tools.map { it.category }.distinct()
}
