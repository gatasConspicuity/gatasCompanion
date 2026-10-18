import SwiftUI

struct WatchDashboard: View {
    @EnvironmentObject private var session: WatchSession
    @State private var confirmStop = false
    @State private var stoppingState: WatchSnapshot?

    var body: some View {
        NavigationStack {
            List {
                WatchConnectionHeader()
                if let state = session.snapshot {
                    Section {
                        // One compact card, rather than a separate Watch list
                        // row for each label, keeps the session glanceable.
                        VStack(alignment: .leading, spacing: 6) {
                            if state.activeDevice != nil {
                                Text(state.selectedAircraftName).font(.headline).minimumScaleFactor(0.7)
                                Text(state.activeDevice?.name ?? "GATAS").font(.caption)
                                if let id = state.gatasId {
                                    Text("GATAS \(id)").font(.caption2).foregroundStyle(.secondary)
                                }
                                Divider()
                                StatusRow(title: "Bluetooth", healthy: state.bleConnected,
                                          detail: state.connecting ? "Connecting" : (state.bleConnected ? "Connected" : "Disconnected"))
                                StatusRow(title: "Relay", healthy: state.udpHealthy, detail: state.udpHealthy ? "Connected" : "Waiting")
                                StatusRow(title: "GDL90", healthy: state.gdl90Enabled && state.diagnostics["GDL90 state"] == "Sending",
                                          detail: state.gdl90Enabled ? (state.diagnostics["GDL90 state"] ?? "Waiting") : "Disabled")
                            } else {
                                Text("Bridge stopped").font(.headline)
                                Text("Choose a device to connect.").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                    if state.running, let status = state.liveActivityStatus, let notice = status.notice {
                        Section {
                            WatchPhoneContinuationNotice(status: status, notice: notice)
                        }
                    }
                    Section {
                        NavigationLink { WatchDevices() } label: { Label("Devices", systemImage: "antenna.radiowaves.left.and.right") }
                        NavigationLink { WatchAircraftList() } label: { Label("Aircraft", systemImage: "airplane") }
                        NavigationLink { WatchSettings() } label: { Label("Settings", systemImage: "gearshape") }
                        NavigationLink { WatchDiagnostics() } label: { Label("Diagnostics", systemImage: "waveform.path.ecg") }
                        if state.running || state.activeDevice != nil {
                            Button("Stop bridge", role: .destructive) { stoppingState = state; confirmStop = true }
                                .disabled(!session.canStop)
                        }
                    }
                    if let error = state.lastError { Section("Device error") { Text(error).foregroundStyle(.orange) } }
                } else {
                    Text("Open GATAS Companion on your paired iPhone and allow Bluetooth access. Then refresh here.")
                        .font(.callout)
                }
                WatchCommandMessage()
                Button("Refresh", systemImage: "arrow.clockwise") { session.refresh() }
            }
            .navigationTitle("GATAS")
            .confirmationDialog("Stop the bridge?", isPresented: $confirmStop, titleVisibility: .visible) {
                Button("Stop bridge", role: .destructive) { session.send(action: "stop", observedState: stoppingState) }
            } message: { Text("Traffic forwarding on your iPhone will stop.") }
        }
    }
}

private struct WatchPhoneContinuationNotice: View {
    let status: WatchLiveActivityStatus
    let notice: String
    @State private var handoffRequestedAt: Date?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if status.canContinueOnPhone {
                Button {
                    handoffRequestedAt = Date()
                } label: {
                    Label(notice, systemImage: "iphone.and.arrow.forward")
                        .font(.caption)
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                }
                .accessibilityHint("Offers Handoff on your iPhone. Open its App Switcher to continue.")
                if handoffRequestedAt != nil {
                    Text("Unlock your iPhone, open the App Switcher and tap the GatasCompanion Handoff banner. Keep this screen open.")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            } else {
                Label(notice, systemImage: "iphone")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        // SwiftUI owns the activity's lifetime with this view. Only a deliberate
        // tap advertises Handoff; a status refresh never opens or signals the phone.
        .userActivity(CompanionHandoff.showLiveActivity,
                      element: status.canContinueOnPhone ? handoffRequestedAt : nil) { requestedAt, activity in
            CompanionHandoff.configure(activity, requestedAt: requestedAt)
        }
    }
}

struct WatchConnectionHeader: View {
    @EnvironmentObject private var session: WatchSession
    var body: some View {
        // Time-based freshness must be reevaluated even when no packet arrives.
        TimelineView(.periodic(from: .now, by: 5)) { context in
            VStack(alignment: .leading, spacing: 4) {
                let live = WatchFreshness.canControl(receivedLiveAt: session.receivedLiveAt, now: context.date)
                Label(live ? "iPhone connected" : "Last known status", systemImage: live ? "iphone" : "iphone.slash")
                    .foregroundStyle(live ? .green : .orange)
                if let state = session.snapshot {
                    Text("Updated \(Date(timeIntervalSince1970: state.updatedAt), style: .relative) ago")
                        .font(.caption2).foregroundStyle(.secondary)
                }
            }
            .font(.caption)
        }
    }
}

struct WatchCommandMessage: View {
    @EnvironmentObject private var session: WatchSession
    var body: some View {
        if let message = session.message {
            Section {
                if session.sending || session.awaitingConfirmation != nil { ProgressView() }
                Text(message).font(.caption).foregroundStyle(session.messageIsError ? .orange : .secondary)
            }
        }
    }
}

private struct StatusRow: View {
    let title: String
    let healthy: Bool
    let detail: String
    var body: some View {
        HStack(alignment: .top) {
            Label(title, systemImage: healthy ? "checkmark.circle.fill" : "circle.dashed")
                .foregroundStyle(healthy ? .green : .secondary)
            Spacer(minLength: 4)
            Text(detail).foregroundStyle(.secondary).multilineTextAlignment(.trailing)
        }.font(.caption2)
    }
}

struct WatchAircraftList: View {
    @EnvironmentObject private var session: WatchSession
    var body: some View {
        List {
            WatchConnectionHeader()
            if let state = session.snapshot {
                if state.aircraftLoading { ProgressView("Loading details…") }
                if let error = state.aircraftError { Text(error).foregroundStyle(.orange) }
                if state.aircraft.isEmpty { Text("Connect to GATAS to load its aircraft list.") }
                ForEach(state.aircraft) { aircraft in
                    Button {
                        session.send(action: "setAircraft", aircraft: aircraft, observedState: state)
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(aircraft.displayName).bold()
                                if state.selectedIcaoAddress == aircraft.id { Image(systemName: "checkmark") }
                            }
                            Text("\(aircraft.aircraftType) · \(aircraft.icaoHexCode)")
                                .font(.caption).foregroundStyle(.secondary)
                            if state.pendingAircraft == aircraft.id { Text("Waiting for GATAS…").font(.caption) }
                        }
                    }.disabled(!session.canControl || !state.bleConnected || state.pendingAircraft != nil)
                }
                Button("Refresh details") { session.send(action: "refreshAircraft") }.disabled(!session.canControl)
            }
            WatchCommandMessage()
        }.navigationTitle("Aircraft")
    }
}

struct WatchDevices: View {
    @EnvironmentObject private var session: WatchSession
    @State private var switchingTo: WatchDevice?
    @State private var switchingState: WatchSnapshot?
    @State private var removing: WatchDevice?
    var body: some View {
        List {
            WatchConnectionHeader()
            if let state = session.snapshot {
                if state.devices.isEmpty {
                    Section("Saved devices") { Text("No saved devices") }
                }
                ForEach(state.devices) { device in
                    Section {
                        if state.activeDevice?.id == device.id {
                            Text("Active device").font(.caption).foregroundStyle(.secondary)
                        }
                        // Keep one action per List row. Multiple automatic
                        // buttons in a shared VStack can all receive the row's
                        // tap, opening Remove while Connect also runs.
                        Button {
                            if let active = state.activeDevice, active.id != device.id {
                                switchingState = state
                                switchingTo = device
                            } else { session.send(action: "start", device: device, observedState: state) }
                        } label: {
                            Label("Connect", systemImage: "link")
                                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                        }
                        .accessibilityLabel("Connect to \(device.name)")
                        .disabled(!session.canControl)

                        Button(role: .destructive) { removing = device } label: {
                            Label("Remove", systemImage: "trash")
                                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                        }
                        .accessibilityLabel("Remove \(device.name)")
                        .disabled(!session.canControl || state.activeDevice?.id == device.id)
                    } header: {
                        Text(device.name).font(.headline)
                    }
                }
                Section("Find GATAS near iPhone") {
                    if state.scanning { ProgressView("Searching…") }
                    if let error = state.scanError { Text(error).foregroundStyle(.orange) }
                    Button(state.scanning ? "Stop Watch search" : "Search for 30 seconds") {
                        session.send(action: state.scanning ? "stopScan" : "startScan")
                    }.disabled(!session.canControl)
                    ForEach(state.discoveredDevices.filter { found in !state.devices.contains { $0.id == found.id } }) { device in
                        Button("Save \(device.name)") { session.send(action: "saveDevice", device: device) }
                            .disabled(!session.canControl)
                    }
                }
            }
            WatchCommandMessage()
        }
        .navigationTitle("Devices")
        .confirmationDialog("Switch device?", isPresented: Binding(get: { switchingTo != nil }, set: { if !$0 { switchingTo = nil } }), titleVisibility: .visible) {
            if let device = switchingTo {
                Button("Connect to \(device.name)") {
                    session.send(action: "start", device: device, observedState: switchingState)
                    switchingTo = nil
                }
            }
        } message: { Text("The current bridge connection will stop.") }
        .confirmationDialog("Remove saved device?", isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } }), titleVisibility: .visible) {
            if let device = removing {
                Button("Remove \(device.name)", role: .destructive) { session.send(action: "deleteDevice", device: device); removing = nil }
            }
        }
    }
}

struct WatchSettings: View {
    @EnvironmentObject private var session: WatchSession
    @State private var restartingState: WatchSnapshot?
    var body: some View {
        List {
            WatchConnectionHeader()
            if let state = session.snapshot {
                Section("GATAS device") {
                    if let control = state.deviceControl {
                        if control.checking { Text("Checking firmware support…").font(.caption) }
                        if control.bootId == nil { Text("Device actions require updated GATAS firmware.").font(.caption) }
                        if let dirty = control.hasUnsavedChanges {
                            Text(dirty ? "Unsaved configuration" : "Configuration saved").font(.caption)
                        }
                        Button("Save configuration") { session.send(action: "saveConfiguration", observedState: state) }
                            .frame(minHeight: 44)
                            .disabled(!session.canControl || !state.bleConnected || !control.supports(1) || state.pendingAircraft != nil || state.pendingWifiMode != nil)
                        Button("Restart GATAS", role: .destructive) { restartingState = state }
                            .frame(minHeight: 44)
                            .disabled(!session.canControl || !state.bleConnected || !control.supports(2) || state.pendingAircraft != nil || state.pendingWifiMode != nil)
                        if let operation = control.operation { Text(operation.message).font(.caption) }
                    } else {
                        Text("Update the iPhone app to use GATAS device actions.").font(.caption)
                    }
                }
                Section("GDL90 on iPhone") {
                    Toggle("Enable bridge", isOn: Binding(get: { state.gdl90Enabled }, set: { session.send(action: "setGdl90", enabled: $0) }))
                        .disabled(!session.canControl)
                }
                Section("GATAS Wi-Fi") {
                    Text("Current: \(state.wifiMode ?? "Unavailable")")
                    ForEach(["AP", "CLIENT"], id: \.self) { mode in
                        Button(mode == state.wifiMode ? "\(mode) ✓" : mode) {
                            session.send(action: "setWifi", mode: mode, observedState: state)
                        }
                            .disabled(!session.canControl || !state.bleConnected || !["AP", "CLIENT"].contains(state.wifiMode ?? "") || state.pendingWifiMode != nil)
                    }
                    if let message = state.wifiMessage { Text(message).font(.caption).foregroundStyle(.secondary) }
                }
            }
            WatchCommandMessage()
        }
        .navigationTitle("Settings")
        .confirmationDialog("Restart GATAS?", isPresented: Binding(get: { restartingState != nil }, set: { if !$0 { restartingState = nil } }), titleVisibility: .visible) {
            Button("Restart GATAS", role: .destructive) {
                session.send(action: "restartGatas", observedState: restartingState)
                restartingState = nil
            }
        } message: {
            Text("Traffic forwarding will pause. Unsaved configuration remains available until power is disconnected.")
        }
    }
}

struct WatchDiagnostics: View {
    @EnvironmentObject private var session: WatchSession
    var body: some View {
        List {
            WatchConnectionHeader()
            if let state = session.snapshot {
                Section("Last event") { Text(state.lastEvent) }
                if let status = state.liveActivityStatus {
                    Section("Live Activity") { Text(status.label) }
                }
                ForEach(state.diagnostics.keys.sorted(), id: \.self) { key in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(key).font(.caption).foregroundStyle(.secondary)
                        Text(state.diagnostics[key] ?? "—").font(.callout)
                    }
                }
            }
        }.navigationTitle("Diagnostics")
    }
}
