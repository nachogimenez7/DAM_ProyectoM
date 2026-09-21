import SwiftUI

enum TraidoresTheme {
    static let gold = Color(red: 212 / 255, green: 162 / 255, blue: 78 / 255)
    static let ink = Color(red: 26 / 255, green: 21 / 255, blue: 16 / 255)
    static let panel = Color(red: 42 / 255, green: 35 / 255, blue: 24 / 255)
    static let border = Color(red: 107 / 255, green: 79 / 255, blue: 42 / 255)
    static let text = Color(red: 240 / 255, green: 230 / 255, blue: 210 / 255)
    static let secondary = Color(red: 196 / 255, green: 182 / 255, blue: 156 / 255)

    static func title(_ size: CGFloat, relativeTo style: Font.TextStyle = .title) -> Font {
        .custom("BreeSerif-Regular", size: size, relativeTo: style)
    }
}

struct MenuBackground: View {
    var body: some View {
        GeometryReader { geometry in
            Image("fondo_menu")
                .resizable()
                .scaledToFill()
                .frame(width: geometry.size.width, height: geometry.size.height)
                .clipped()
                .overlay(.black.opacity(0.18))
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }
}

struct TraidoresButtonStyle: ButtonStyle {
    var prominent = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(TraidoresTheme.title(19, relativeTo: .headline))
            .tracking(1)
            .frame(maxWidth: .infinity, minHeight: 28)
            .padding(.vertical, 13)
            .padding(.horizontal, 18)
            .foregroundStyle(prominent ? TraidoresTheme.ink : TraidoresTheme.text)
            .background {
                RoundedRectangle(cornerRadius: 10)
                    .fill(prominent
                          ? LinearGradient(colors: [Color(red: 232 / 255, green: 184 / 255, blue: 75 / 255), TraidoresTheme.gold], startPoint: .top, endPoint: .bottom)
                          : LinearGradient(colors: [TraidoresTheme.panel, TraidoresTheme.panel], startPoint: .top, endPoint: .bottom))
            }
            .overlay {
                RoundedRectangle(cornerRadius: 10)
                    .stroke(prominent ? TraidoresTheme.gold : TraidoresTheme.border, lineWidth: 1)
            }
            .opacity(configuration.isPressed ? 0.72 : 1)
    }
}

struct MenuPage<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            MenuBackground()
            VStack(spacing: 0) {
                MenuHeader(title: title, back: dismiss.callAsFunction)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)
                ScrollView {
                    VStack(alignment: .leading, spacing: 18, content: content)
                        .padding(.horizontal, 20)
                        .padding(.bottom, 20)
                        .frame(maxWidth: 560)
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .foregroundStyle(TraidoresTheme.text)
        .toolbar(.hidden, for: .navigationBar)
    }
}

struct MenuHeader: View {
    let title: String
    let back: () -> Void

    var body: some View {
        ZStack {
            Text(title).font(TraidoresTheme.title(19)).foregroundStyle(TraidoresTheme.text)
            HStack {
                Button(action: back) {
                    Image(systemName: "chevron.left").font(.headline)
                        .frame(width: 44, height: 44)
                        .background(TraidoresTheme.panel.opacity(0.94), in: Circle())
                        .overlay(Circle().stroke(TraidoresTheme.border))
                }
                .foregroundStyle(TraidoresTheme.text)
                Spacer()
            }
        }
        .frame(maxWidth: .infinity)
    }
}

struct InformationCard: View {
    let title: String
    let message: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(TraidoresTheme.title(23)).foregroundStyle(TraidoresTheme.gold)
            Text(message).font(.body).foregroundStyle(TraidoresTheme.text)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(TraidoresTheme.panel.opacity(0.96), in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(TraidoresTheme.border, lineWidth: 1))
    }
}
