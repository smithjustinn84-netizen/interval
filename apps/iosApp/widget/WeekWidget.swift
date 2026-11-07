import SwiftUI
import WidgetKit

// The Home Screen widget, Android's `WeekWidget` on iOS: the week's ring (this week's workouts of both kinds over
// the goal set on Plan, or the count alone with no goal), its words and the streak, with Workout and Lift, which
// open the app as its quick actions do (`interval://shortcut/<id>`, handled by `SceneDelegate`, so a workout
// already running is kept). Swift alone: the extension can't link the app's Kotlin, so the app writes each
// standing to the App Group as a `WeekSnapshot` (`WidgetFeed.kt`) and asks WidgetKit to redraw.

/// `WeekSnapshot` in apps/app/src/data/WeekSnapshot.kt, as `WidgetFeed.kt` stores it: the names are the JSON's.
struct WeekSnapshot: Decodable, Equatable {
    let count: Int
    let goal: Int?
    let streak: Int
    /// The Monday of the week it stands for, an epoch day in the phone's time zone.
    let weekStart: Int

    static let appGroup = "group.dev.justinsmith.interval"
    static let key = "week"

    /// What the app last wrote, or nil before it has run.
    static func stored() -> WeekSnapshot? {
        guard let json = UserDefaults(suiteName: appGroup)?.string(forKey: key) else { return nil }
        return try? JSONDecoder().decode(WeekSnapshot.self, from: Data(json.utf8))
    }

    var goalMet: Bool { goal.map { count >= $0 } ?? false }

    /// As the week stands on `date` with nothing done since: a later week holds nothing, and the streak survives
    /// one turn only if this week met its goal (`weeksMeetingGoal` counts back from last week until this one does).
    func asOf(_ date: Date, calendar: Calendar = .current) -> WeekSnapshot {
        let week = Days.weekStart(of: Days.epochDay(of: date, calendar: calendar))
        switch week - weekStart {
        case ...0: return self
        case 7: return WeekSnapshot(count: 0, goal: goal, streak: goalMet ? streak : 0, weekStart: week)
        default: return WeekSnapshot(count: 0, goal: goal, streak: 0, weekStart: week)
        }
    }

    /// Before the app has run: an empty week with no goal.
    static let empty = WeekSnapshot(count: 0, goal: nil, streak: 0, weekStart: 0)
    /// The gallery's preview: a week part way to its goal, on a streak, so each part shows (Android's PREVIEW_WEEK).
    static let preview = WeekSnapshot(count: 3, goal: 5, streak: 2, weekStart: 0) // shown as is, never `asOf`
}

/// Epoch days as core-time counts them (`CalendarDays.kt`): day 0 is Thursday 1 January 1970, weeks start Monday.
enum Days {
    static func epoch(_ calendar: Calendar) -> Date {
        calendar.date(from: DateComponents(year: 1970, month: 1, day: 1))!
    }

    static func epochDay(of date: Date, calendar: Calendar) -> Int {
        calendar.dateComponents([.day], from: epoch(calendar), to: calendar.startOfDay(for: date)).day ?? 0
    }

    static func weekStart(of epochDay: Int) -> Int {
        let weekday = ((epochDay + 3) % 7 + 7) % 7 // 0 is Monday
        return epochDay - weekday
    }

    /// Local midnight starting the week after the one holding `date`.
    static func nextWeek(after date: Date, calendar: Calendar) -> Date {
        let start = weekStart(of: epochDay(of: date, calendar: calendar))
        return calendar.date(byAdding: .day, value: start + 7, to: epoch(calendar))!
    }
}

struct WeekEntry: TimelineEntry {
    let date: Date
    let week: WeekSnapshot
}

struct WeekProvider: TimelineProvider {
    func placeholder(in context: Context) -> WeekEntry { WeekEntry(date: .now, week: .preview) }

    func getSnapshot(in context: Context, completion: @escaping (WeekEntry) -> Void) {
        let week = context.isPreview ? .preview : (WeekSnapshot.stored() ?? .empty).asOf(.now)
        completion(WeekEntry(date: .now, week: week))
    }

    // Now, and the week's turn at Monday's midnight (Android's `WidgetDayAlarm`): the app needn't run for the ring
    // to empty. A new timeline is asked for after the turn, and whenever the app writes a standing.
    func getTimeline(in context: Context, completion: @escaping (Timeline<WeekEntry>) -> Void) {
        let now = Date.now
        let stored = WeekSnapshot.stored() ?? .empty
        let turn = Days.nextWeek(after: now, calendar: .current)
        let entries = [WeekEntry(date: now, week: stored.asOf(now)), WeekEntry(date: turn, week: stored.asOf(turn))]
        completion(Timeline(entries: entries, policy: .atEnd))
    }
}

/// The workout page's colours (core's `PhaseColors`, Android's res/values/colors.xml), on every appearance: a
/// widget sits on the wallpaper as the workout sits on its dark ground.
enum Palette {
    static let card = Color(red: 0x16 / 255, green: 0x1A / 255, blue: 0x20 / 255) // control
    static let done = Color(red: 0x5B / 255, green: 0x8C / 255, blue: 0xFF / 255)
    static let upcoming = Color(red: 0x23 / 255, green: 0x2B / 255, blue: 0x35 / 255)
    static let onGround = Color(red: 0xF1 / 255, green: 0xF4 / 255, blue: 0xF8 / 255)
    static let onGroundDim = Color(red: 0x97 / 255, green: 0xA3 / 255, blue: 0xB3 / 255)
    static let ink = Color(red: 0x0B / 255, green: 0x0D / 255, blue: 0x10 / 255)
}

/// The app's `Shortcut` ids, as the quick actions in Info.plist carry them.
enum Start {
    case workout, lift

    var url: URL {
        switch self {
        case .workout: URL(string: "interval://shortcut/start-workout")!
        case .lift: URL(string: "interval://shortcut/lift-session")!
        }
    }

    var label: LocalizedStringKey {
        switch self {
        case .workout: "Workout"
        case .lift: "Lift"
        }
    }

    var description: Text {
        switch self {
        case .workout: Text("Start a workout")
        case .lift: Text("Start a Lift session")
        }
    }
}

struct WeekWidgetView: View {
    let week: WeekSnapshot
    @Environment(\.widgetFamily) private var family

    var body: some View {
        Group {
            if family == .systemMedium {
                HStack(spacing: 16) {
                    Ring(week: week)
                    Words(week: week, alignment: .leading).frame(maxWidth: .infinity, alignment: .leading)
                    VStack(spacing: 8) {
                        StartButton(start: .workout)
                        StartButton(start: .lift)
                    }
                    .frame(width: 96)
                }
            } else {
                VStack(spacing: 6) {
                    Ring(week: week, diameter: 60, stroke: 7, figure: 18)
                    Words(week: week, alignment: .center)
                    Spacer(minLength: 0)
                    HStack(spacing: 8) {
                        StartButton(start: .workout)
                        StartButton(start: .lift)
                    }
                }
            }
        }
        .containerBackground(Palette.card, for: .widget)
    }
}

/// The ring with its figure over it: "3/4", or the count alone with no goal.
struct Ring: View {
    let week: WeekSnapshot
    var diameter: CGFloat = 76
    var stroke: CGFloat = 8
    var figure: CGFloat = 22

    var body: some View {
        let share = week.goal.map { $0 > 0 ? min(Double(week.count) / Double($0), 1) : 1 } ?? 0
        ZStack {
            Circle().stroke(Palette.upcoming, lineWidth: stroke)
            if share > 0 {
                Circle()
                    .trim(from: 0, to: share)
                    .stroke(Palette.done, style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                    .rotationEffect(.degrees(-90))
            }
            Text(verbatim: week.goal.map { "\(week.count)/\($0)" } ?? "\(week.count)")
                .font(.system(size: figure, weight: .bold))
                .foregroundStyle(Palette.onGround)
                .minimumScaleFactor(0.6)
        }
        .padding(stroke / 2)
        .frame(width: diameter, height: diameter)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            week.goal.map { Text("\(week.count) of \($0) workouts this week") }
                ?? Text("^[\(week.count) workout](inflect: true) this week")
        )
    }
}

/// Where the week stands in words, and the streak when there is one.
struct Words: View {
    let week: WeekSnapshot
    let alignment: HorizontalAlignment

    var body: some View {
        VStack(alignment: alignment, spacing: 2) {
            caption
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(Palette.onGround)
            if week.streak > 0 {
                Text("\(week.streak)-week streak")
                    .font(.system(size: 14))
                    .foregroundStyle(Palette.onGroundDim)
            }
        }
        .multilineTextAlignment(alignment == .center ? .center : .leading)
        .lineLimit(1)
        .minimumScaleFactor(0.8)
    }

    private var caption: Text {
        guard let goal = week.goal else { return Text("This week") }
        return week.goalMet ? Text("Goal met this week") : Text("\(goal - week.count) to go")
    }
}

/// Workout on the done fill, Lift beside it on the upcoming fill; each opens the app on its quick action.
struct StartButton: View {
    let start: Start

    var body: some View {
        Link(destination: start.url) {
            Text(start.label)
                .font(.system(size: 14, weight: .medium))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .foregroundStyle(start == .workout ? Palette.ink : Palette.onGround)
                .frame(maxWidth: .infinity, minHeight: 36)
                .background(start == .workout ? Palette.done : Palette.upcoming, in: Capsule())
        }
        .accessibilityLabel(start.description)
    }
}

@main
struct WeekWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "WeekWidget", provider: WeekProvider()) { entry in
            WeekWidgetView(week: entry.week)
        }
        .configurationDisplayName("This week")
        .description("This week's workouts against your goal, with Workout and Lift to start one")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
