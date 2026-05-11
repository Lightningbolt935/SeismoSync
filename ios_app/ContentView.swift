import SwiftUI

struct ContentView: View {
    @StateObject private var manager = ShakeAlertManager.shared

    var body: some View {
        VStack(spacing: 18) {
            VStack(alignment: .leading, spacing: 4) {
                Text(manager.mainStatus)
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(manager.isServiceRunning ? .green : .red)

                Text(manager.subStatus)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
            .background(Color(.systemGray6))
            .cornerRadius(12)

            Toggle("Background Monitoring", isOn: Binding(
                get: { manager.isServiceRunning },
                set: { value in
                    if value {
                        manager.startService()
                    } else {
                        manager.stopService()
                    }
                }
            ))
            .padding()
            .background(Color(.systemGray6))
            .cornerRadius(12)

            Button(action: {
                manager.manualTrigger()
            }) {
                Text("Send Test Alert")
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding()
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.orange)

            VStack(alignment: .leading) {
                Text("Terminal Logs")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .padding(.bottom, 4)

                ScrollViewReader { proxy in
                    ScrollView {
                        VStack(alignment: .leading, spacing: 5) {
                            ForEach(0..<manager.logs.count, id: \.self) { index in
                                Text(manager.logs[index])
                                    .font(.system(.caption, design: .monospaced))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                    }
                    .onChange(of: manager.logs.count) { _ in
                        proxy.scrollTo(manager.logs.count - 1)
                    }
                }
            }
                .padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(.systemBackground))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(.separator), lineWidth: 1)
            )
        }
        .padding()
        .navigationTitle("Shake Alert")
        .toolbar {
            ToolbarItem(placement: .bottomBar) {
                HStack {
                    Spacer()
                    Text("v\(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0")")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
