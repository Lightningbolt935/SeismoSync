import SwiftUI

struct ContentView: View {
    @StateObject private var manager = ShakeAlertManager.shared

    var body: some View {
        VStack(spacing: 20) {
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
                Text("MANUAL TRIGGER")
                    .fontWeight(.bold)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.orange)
                    .foregroundColor(.white)
                    .cornerRadius(12)
            }

            VStack(alignment: .leading) {
                Text("TERMINAL LOGS")
                    .font(.caption)
                    .fontWeight(.bold)
                    .padding(.bottom, 2)

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
            .background(Color.black)
            .foregroundColor(.green)
            .cornerRadius(12)
        }
        .padding()
        .navigationTitle("Shake Alert")
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
