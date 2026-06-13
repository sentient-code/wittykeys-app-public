package project.witty.keys.debug

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import project.witty.keys.R

/**
 * Debug-only launch UI state harness for frozen UI capture/regression.
 *
 * This Activity is included only in debug builds via app/src/debug. It renders
 * deterministic non-hold launch states without touching Overlay, Keyboard AI,
 * or Sessions production surfaces.
 */
class LaunchStateActivity : Activity() {

    companion object {
        const val EXTRA_STATE = "debug_state"

        private const val BG = 0xFF0D0D0F.toInt()
        private const val SURFACE = 0xFF161619.toInt()
        private const val SURFACE_2 = 0xFF1F1F24.toInt()
        private const val SURFACE_3 = 0xFF2A2A30.toInt()
        private const val TEXT = 0xFFF3F4F6.toInt()
        private const val MUTED = 0xFFA0A0A8.toInt()
        private const val DIM = 0xFF606068.toInt()
        private const val ACCENT = 0xFF6CB4EE.toInt()
        private const val PURPLE = 0xFFA78BFA.toInt()
        private const val GREEN = 0xFF4ADE80.toInt()
        private const val ORANGE = 0xFFFB923C.toInt()
        private const val RED = 0xFFF87171.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        window.statusBarColor = BG
        window.navigationBarColor = BG
        val state = intent.getStringExtra(EXTRA_STATE) ?: "hm-anonymous-ready"
        setContentView(render(resolveState(state)))
    }

    private fun render(state: LaunchState): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shellBackground()
            setPadding(dp(17), dp(40), dp(17), dp(18))
            layoutParams = ViewGroup.LayoutParams(match, match)
        }

        root.addView(topbar(state.topTitle, state.topSubtitle))

        val body = when (state.type) {
            ScreenType.HOME -> homeBody(state)
            ScreenType.SETTINGS -> settingsBody(state)
            ScreenType.SUBSCRIPTION -> subscriptionBody(state)
            ScreenType.ACCOUNT -> accountBody(state)
            ScreenType.PRIVACY -> privacyBody(state)
            ScreenType.PERMISSION -> permissionBody(state)
            ScreenType.QUOTA -> quotaBody(state)
        }
        root.addView(body, LinearLayout.LayoutParams(match, 0, 1f))
        if (state.type == ScreenType.HOME || state.type == ScreenType.SETTINGS) {
            root.addView(bottomTabs(if (state.type == ScreenType.SETTINGS) "Settings" else "Home"))
        }
        return root
    }

    private fun homeBody(state: LaunchState): View {
        return verticalScroll {
            addView(heading(state.heroTitle, state.body, compact = true))
            addView(productStage(state))
            addView(commandGrid(state.cards))
            addView(creditStrip(state.meter, state.walletLabel))
        }
    }

    private fun productStage(state: LaunchState): View {
        val empty = state.dataState == "hm-quota-empty"
        return FrameLayout(this).apply {
            background = elevatedCardBg(dp(28), stroke = 0x18FFFFFF)
            clipToOutline = true

            addView(row {
                addView(text(state.stageCaption, 10f, MUTED, Typeface.BOLD), LinearLayout.LayoutParams(0, wrap, 1f))
                addView(pill(state.walletLabel, if (empty) RED else GREEN, darkText = false))
                if (state.walletLabel.contains("credit", ignoreCase = true)) {
                    addView(pill(if (state.walletLabel == "Plus credits") "Manage" else "Upgrade", ACCENT, true), LinearLayout.LayoutParams(wrap, dp(24)).apply {
                        marginStart = dp(7)
                    })
                }
            }, FrameLayout.LayoutParams(match, wrap).apply {
                leftMargin = dp(14)
                topMargin = dp(13)
                rightMargin = dp(14)
            })

            addView(keyboardPeek(), FrameLayout.LayoutParams(match, wrap, Gravity.BOTTOM).apply {
                leftMargin = dp(14)
                rightMargin = dp(14)
                bottomMargin = dp(12)
            })

            addView(overlayPreview(state), FrameLayout.LayoutParams(match, wrap).apply {
                leftMargin = dp(14)
                topMargin = dp(112)
                rightMargin = dp(14)
            })

            addView(logoBubble(empty).apply { elevation = dp(8).toFloat() }, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(50)
                rightMargin = dp(18)
            })
        }.withMargins(top = 15).apply {
            layoutParams = LinearLayout.LayoutParams(match, dp(318)).apply {
                topMargin = dp(15)
            }
        }
    }

    private fun overlayPreview(state: LaunchState): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg(dp(18), stroke = 0x18FFFFFF)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        v.addView(row {
            addView(circleText("QR", GREEN, 34), LinearLayout.LayoutParams(dp(34), dp(34)))
            addView(LinearLayout(this@LaunchStateActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(state.stageTitle, 14f, TEXT, Typeface.BOLD))
                addView(text(state.stageSubtitle, 11f, MUTED))
            }, LinearLayout.LayoutParams(0, wrap, 1f).apply { marginStart = dp(10) })
            addView(pill(if (state.dataState == "hm-quota-empty") "Paused" else "Open", SURFACE_3, false))
        })
        v.addView(cardText("Priya - WhatsApp", state.sampleMessage), topMargin = 10)
        v.addView(suggestion(state.sampleReply), topMargin = 8)
        return v
    }

    private fun keyboardPeek(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = quietCardBg(dp(16), stroke = 0x10FFFFFF)
            setPadding(dp(9), dp(9), dp(9), dp(9))
        }
        box.addView(row {
            listOf("Reply", "Tone", "Chat").forEach {
                addView(TextView(this@LaunchStateActivity).apply {
                    text = it
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xB3FFFFFF.toInt())
                    gravity = Gravity.CENTER
                    background = rounded(0x0FFFFFFF, dp(11))
                }, LinearLayout.LayoutParams(0, dp(27), 1f).apply { marginEnd = dp(6) })
            }
        })
        repeat(3) { rowIndex ->
            box.addView(row {
                repeat(10) { keyIndex ->
                    addView(View(this@LaunchStateActivity).apply {
                        background = if ((rowIndex + keyIndex) % 4 == 0) {
                            gradientBg(intArrayOf(0xFF343743.toInt(), 0xFF25272F.toInt()), dp(6), 0x08FFFFFF)
                        } else {
                            gradientBg(intArrayOf(0xFF24262E.toInt(), 0xFF181A20.toInt()), dp(6), 0x08FFFFFF)
                        }
                    }, LinearLayout.LayoutParams(0, dp(16), 1f).apply { marginEnd = dp(4) })
                }
            }, topMargin = 6)
        }
        return box
    }

    private fun settingsBody(state: LaunchState): View {
        return verticalScroll {
            addView(heading(state.heroTitle, state.body))
            addView(searchBox("Search settings, permissions, credits"), topMargin = 12)
            state.groups.forEach { group ->
                addView(settingsGroup(group.first, group.second), topMargin = 14)
            }
        }
    }

    private fun subscriptionBody(state: LaunchState): View {
        return verticalScroll {
            addView(plusCard(state))
            addView(sectionRows(state.cards), topMargin = 12)
            addView(bottomActions(state.primary, state.secondary), topMargin = 14)
        }
    }

    private fun accountBody(state: LaunchState): View {
        return verticalScroll {
            addView(trustPanel(state.id, state.heroTitle, state.body))
            if (state.dataState == "acct-auth-options") {
                addView(authOptionsPanel(state), topMargin = 12)
                addView(sectionRows(state.cards), topMargin = 12)
                return@verticalScroll
            }
            addView(sectionRows(state.cards), topMargin = 12)
            addView(bottomActions(state.primary, state.secondary), topMargin = 14)
        }
    }

    private fun privacyBody(state: LaunchState): View {
        return verticalScroll {
            addView(heading(state.heroTitle, state.body))
            addView(trustPanel("AI visibility", "What AI can see", "Only the content you intentionally use with an AI action. Screen capture and notification access are explained before Android asks."), topMargin = 12)
            addView(costList(state.cards), topMargin = 12)
            addView(bottomActions(state.primary, state.secondary), topMargin = 14)
        }
    }

    private fun permissionBody(state: LaunchState): View {
        val frame = FrameLayout(this).apply {
            background = elevatedCardBg(dp(22), stroke = 0x18FFFFFF)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Clear disclosure first. Android permission screen second.", 12f, MUTED, Typeface.BOLD))
            addView(LinearLayout(this@LaunchStateActivity).apply {
                orientation = LinearLayout.VERTICAL
                background = cardBg(dp(22), stroke = 0x22FFFFFF)
                setPadding(dp(16), dp(16), dp(16), dp(16))
                addView(text(state.id, 11f, ACCENT, Typeface.BOLD))
                addView(text(state.heroTitle, 24f, TEXT, Typeface.BOLD), topMargin = 8)
                addView(text(state.body, 13f, MUTED), topMargin = 8)
                addView(sectionRows(state.cards), topMargin = 12)
                addView(bottomActions(state.primary, state.secondary), topMargin = 10)
            }, topMargin = 18)
        }
        frame.addView(content)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            addView(frame, LinearLayout.LayoutParams(match, wrap))
        }
    }

    private fun quotaBody(state: LaunchState): View {
        return verticalScroll {
            addView(walletCard(state))
            addView(bottomActions(state.primary, state.secondary), topMargin = 14)
        }
    }

    private fun plusCard(state: LaunchState): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = elevatedCardBg(dp(24), stroke = 0x18FFFFFF)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        card.addView(text(state.id, 11f, ACCENT, Typeface.BOLD))
        card.addView(text(state.heroTitle, 24f, TEXT, Typeface.BOLD), topMargin = 8)
        card.addView(text(state.body, 13f, MUTED), topMargin = 8)
        card.addView(row {
            addView(planColumn("Free", "Daily essentials stay useful without sign-in.", "20 credits"), LinearLayout.LayoutParams(0, dp(118), 1f))
            addView(planColumn("Plus", "For heavier screen AI, chat, and reply usage.", "More AI", true), LinearLayout.LayoutParams(0, dp(118), 1f).apply {
                marginStart = dp(9)
            })
        }, topMargin = 16)
        card.addView(planBenefit("Upgrade only after value is clear"), topMargin = 14)
        card.addView(planBenefit("Credit limits protect product cost"), topMargin = 6)
        return card
    }

    private fun walletCard(state: LaunchState): View {
        val count = when (state.dataState) {
            "qt-empty-balance" -> "0"
            "qt-low-balance" -> "4"
            "qt-plus-balance" -> "Plus"
            else -> "18"
        }
        val label = if (state.dataState == "qt-plus-balance") "credits active" else "credits left"
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = elevatedCardBg(dp(24), stroke = 0x18FFFFFF)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        card.addView(text(state.id, 11f, ACCENT, Typeface.BOLD))
        card.addView(row {
            addView(LinearLayout(this@LaunchStateActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(count, 44f, TEXT, Typeface.BOLD))
                addView(text(label, 13f, MUTED, Typeface.BOLD))
            }, LinearLayout.LayoutParams(0, wrap, 1f))
            addView(text("AI runs only after the user taps a visible action.", 12f, MUTED), LinearLayout.LayoutParams(dp(138), wrap))
        }, topMargin = 8)
        card.addView(meter(state.meter), topMargin = 16)
        card.addView(costList(state.cards), topMargin = 16)
        return card
    }

    private fun authOptionsPanel(state: LaunchState): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg(dp(22), stroke = 0x18FFFFFF)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(googleButton())
            addView(TextView(this@LaunchStateActivity).apply {
                text = state.secondary
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT)
                gravity = Gravity.CENTER
                background = gradientBg(intArrayOf(0x22FFFFFF, 0x10FFFFFF), dp(8), 0x1AFFFFFF)
            }, LinearLayout.LayoutParams(match, dp(52)).apply { topMargin = dp(10) })
        }
    }

    private fun googleButton(): View {
        return row {
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, dp(8), stroke = 0x1F000000)
            addView(ImageView(this@LaunchStateActivity).apply {
                setImageResource(R.drawable.ic_brand_google)
                contentDescription = "Google"
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(12) })
            addView(text("Continue with Google", 14f, 0xFF1F1F1F.toInt(), Typeface.BOLD))
            layoutParams = LinearLayout.LayoutParams(match, dp(52))
        }
    }

    private fun trustPanel(eyebrow: String, title: String, body: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = elevatedCardBg(dp(22), stroke = 0x18FFFFFF)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(text(eyebrow, 11f, ACCENT, Typeface.BOLD))
            addView(text(title, 24f, TEXT, Typeface.BOLD), topMargin = 8)
            addView(text(body, 13f, MUTED), topMargin = 8)
        }
    }

    private fun commandGrid(cards: List<CardItem>): View {
        val stack = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cards.forEachIndexed { index, card ->
            stack.addView(commandTile(card, index == 0), LinearLayout.LayoutParams(0, dp(112), 1f).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
        return stack.withMargins(top = 11)
    }

    private fun commandTile(card: CardItem, hero: Boolean): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = if (hero) heroCardBg(dp(18)) else cardBg(dp(18))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(text(card.icon, 9.5f, ACCENT, Typeface.BOLD))
            addView(TextView(this@LaunchStateActivity).apply {
                text = card.title
                textSize = 12f
                setTextColor(TEXT)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = true
                maxLines = 2
            }, LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(7) })
            addView(TextView(this@LaunchStateActivity).apply {
                text = card.body
                textSize = 8.5f
                setTextColor(MUTED)
                includeFontPadding = true
                maxLines = 2
            }, LinearLayout.LayoutParams(match, 0, 1f).apply { topMargin = dp(5) })
            addView(TextView(this@LaunchStateActivity).apply {
                text = card.status
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT)
                gravity = Gravity.CENTER
                setPadding(dp(8), 0, dp(8), 0)
                background = rounded(0x17FFFFFF, dp(999))
            }, LinearLayout.LayoutParams(wrap, dp(23)).apply { topMargin = dp(5) })
        }
    }

    private fun commandRow(card: CardItem, hero: Boolean): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = if (hero) heroCardBg(dp(18), stroke = 0x336CB4EE) else cardBg(dp(18))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(circleText(card.icon, if (hero) ACCENT else SURFACE_3, 38))
            addView(LinearLayout(this@LaunchStateActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(card.title, 14f, TEXT, Typeface.BOLD))
                addView(text(card.body, 11f, MUTED))
            }, LinearLayout.LayoutParams(0, wrap, 1f).apply { marginStart = dp(10) })
            addView(pill(card.status, if (card.tone == Tone.DANGER) RED else if (card.tone == Tone.WARN) ORANGE else SURFACE_2, false))
        }
    }

    private fun sectionRows(cards: List<CardItem>): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            cards.forEachIndexed { index, card ->
                addView(settingsRow(card).apply {
                    background = cardBg(dp(18))
                }, LinearLayout.LayoutParams(match, wrap).apply {
                    if (index > 0) topMargin = dp(8)
                })
            }
        }
    }

    private fun settingsGroup(title: String, rows: List<CardItem>): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title.uppercase(), 11f, MUTED, Typeface.BOLD))
            addView(LinearLayout(this@LaunchStateActivity).apply {
                orientation = LinearLayout.VERTICAL
                rows.forEachIndexed { index, card ->
                    addView(settingsRow(card).apply {
                        background = cardBg(dp(18))
                    }, LinearLayout.LayoutParams(match, wrap).apply {
                        if (index > 0) topMargin = dp(8)
                    })
                }
            }, topMargin = 8)
        }
    }

    private fun settingsRow(card: CardItem): View {
        return row {
            setPadding(dp(12), dp(11), dp(12), dp(11))
            addView(circleText(card.icon, SURFACE_3, 36))
            addView(LinearLayout(this@LaunchStateActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(card.title, 13.5f, TEXT, Typeface.BOLD))
                addView(text(card.body, 11f, MUTED))
            }, LinearLayout.LayoutParams(0, wrap, 1f).apply { marginStart = dp(10) })
            addView(control(card.status, card.tone))
        }
    }

    private fun costList(cards: List<CardItem>): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            cards.forEachIndexed { index, card ->
                addView(row {
                    background = cardBg(dp(18))
                    setPadding(dp(12), dp(11), dp(12), dp(11))
                    addView(LinearLayout(this@LaunchStateActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(text(card.title, 13f, TEXT, Typeface.BOLD))
                        addView(text(card.body, 11f, MUTED))
                    }, LinearLayout.LayoutParams(0, wrap, 1f))
                    addView(text(card.status, 13f, ACCENT, Typeface.BOLD))
                }, LinearLayout.LayoutParams(match, wrap).apply {
                    if (index > 0) topMargin = dp(8)
                })
            }
        }
    }

    private fun planLane(title: String, body: String, price: String, plus: Boolean = false): View {
        return row {
            background = if (plus) heroCardBg(dp(16), stroke = 0x336CB4EE) else quietCardBg(dp(16), stroke = 0x10FFFFFF)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(LinearLayout(this@LaunchStateActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(title, 15f, TEXT, Typeface.BOLD))
                addView(text(body, 11f, MUTED))
            }, LinearLayout.LayoutParams(0, wrap, 1f))
            addView(text(price, 13f, if (plus) ACCENT else MUTED, Typeface.BOLD))
        }
    }

    private fun planColumn(title: String, body: String, price: String, plus: Boolean = false): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = if (plus) heroCardBg(dp(18), stroke = 0x336CB4EE) else quietCardBg(dp(18), stroke = 0x10FFFFFF)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(text(title, 14f, TEXT, Typeface.BOLD))
            addView(text(body, 10f, MUTED), LinearLayout.LayoutParams(match, 0, 1f).apply { topMargin = dp(6) })
            addView(text(price, 16f, if (plus) ACCENT else MUTED, Typeface.BOLD))
        }
    }

    private fun planBenefit(copy: String): View = row {
        addView(circleText("OK", GREEN, 20))
        addView(text(copy, 12f, MUTED), LinearLayout.LayoutParams(0, wrap, 1f).apply { marginStart = dp(8) })
    }

    private fun heading(title: String, body: String, compact: Boolean = false): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, if (compact) 24f else 26f, TEXT, Typeface.BOLD))
            addView(text(body, 13f, MUTED), topMargin = 6)
        }
    }

    private fun searchBox(copy: String): View {
        return TextView(this).apply {
            text = copy
            setTextColor(DIM)
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            background = quietCardBg(dp(16), stroke = 0x14FFFFFF)
            setPadding(dp(14), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(match, dp(46))
        }
    }

    private fun bottomActions(primary: String, secondary: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@LaunchStateActivity).apply {
                text = primary
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF071018.toInt())
                gravity = Gravity.CENTER
                background = gradient(intArrayOf(ACCENT, PURPLE), dp(18))
            }, LinearLayout.LayoutParams(match, dp(50)))
            addView(TextView(this@LaunchStateActivity).apply {
                text = secondary
                textSize = 13f
                setTextColor(MUTED)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(match, dp(38)).apply { topMargin = dp(6) })
        }
    }

    private fun creditStrip(meter: Int, label: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg(dp(18))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(row {
                addView(text("AI wallet", 12f, MUTED, Typeface.BOLD), LinearLayout.LayoutParams(0, wrap, 1f))
                addView(text(label, 13f, TEXT, Typeface.BOLD))
                if (label.contains("credit", ignoreCase = true)) {
                    addView(pill(if (label == "Plus credits") "Manage" else "Upgrade", ACCENT, true), LinearLayout.LayoutParams(wrap, dp(26)).apply {
                        marginStart = dp(8)
                    })
                }
            })
            addView(meter(meter), topMargin = 10)
        }.withMargins(top = 12)
    }

    private fun meter(percent: Int): View {
        return FrameLayout(this).apply {
            background = rounded(SURFACE_3, dp(999))
            val fill = View(this@LaunchStateActivity).apply {
                background = gradient(intArrayOf(GREEN, ACCENT), dp(999))
            }
            addView(fill, FrameLayout.LayoutParams(0, match).apply {
                width = dp((percent.coerceIn(0, 100) * 2.6f).toInt())
            })
            layoutParams = LinearLayout.LayoutParams(match, dp(8))
        }
    }

    private fun bottomTabs(active: String): View {
        return row {
            gravity = Gravity.CENTER
            background = cardBg(dp(18))
            setPadding(0, 0, 0, 0)
            listOf("Home", "Chats", "Usage", "Settings").forEach { tab ->
                addView(TextView(this@LaunchStateActivity).apply {
                    text = tab
                    textSize = 9f
                    typeface = if (tab == active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    setTextColor(if (tab == active) 0xFFBDE3FF.toInt() else 0x80FFFFFF.toInt())
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(0, dp(54), 1f))
            }
        }.withMargins(top = 0)
    }

    private fun topbar(title: String, subtitle: String): View {
        return row {
            gravity = Gravity.CENTER_VERTICAL
            addView(logoSquare(), LinearLayout.LayoutParams(dp(34), dp(34)))
            addView(LinearLayout(this@LaunchStateActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(title, 13f, TEXT, Typeface.BOLD))
                addView(text(subtitle, 10f, MUTED))
            }, LinearLayout.LayoutParams(0, wrap, 1f).apply { marginStart = dp(10) })
            addView(TextView(this@LaunchStateActivity).apply {
                text = "..."
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xD1FFFFFF.toInt())
                gravity = Gravity.CENTER
                background = rounded(0x0FFFFFFF, dp(11), stroke = 0x0FFFFFFF)
            }, LinearLayout.LayoutParams(dp(34), dp(34)))
        }.withMargins(bottom = 14)
    }

    private fun logoBubble(empty: Boolean): View {
        return FrameLayout(this).apply {
            addView(TextView(this@LaunchStateActivity).apply {
                text = "W"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (empty) MUTED else Color.WHITE)
                gravity = Gravity.CENTER
                background = gradient(if (empty) intArrayOf(SURFACE_2, SURFACE_3) else intArrayOf(0xFF4A4A52.toInt(), 0xFF3A3A42.toInt()), dp(999))
            }, FrameLayout.LayoutParams(match, match))
            if (!empty) {
                addView(TextView(this@LaunchStateActivity).apply {
                    text = "4"
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xFF07130A.toInt())
                    gravity = Gravity.CENTER
                    background = rounded(GREEN, dp(999), stroke = BG)
                }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END).apply {
                    topMargin = -dp(3)
                    rightMargin = -dp(3)
                })
            }
        }
    }

    private fun logoSquare(): TextView {
        return TextView(this).apply {
            text = "W"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = gradient(intArrayOf(0x336CB4EE, 0x2EA78BFA), dp(12))
        }
    }

    private fun cardText(meta: String, body: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = quietCardBg(dp(14), stroke = 0x10FFFFFF)
            setPadding(dp(10), dp(9), dp(10), dp(9))
            addView(text(meta, 10.5f, MUTED, Typeface.BOLD))
            addView(text(body, 12.5f, TEXT), topMargin = 4)
        }
    }

    private fun suggestion(copy: String): View {
        return row {
            background = heroCardBg(dp(16), stroke = 0x336CB4EE)
            setPadding(dp(10), dp(9), dp(10), dp(9))
            addView(pill("AI", ACCENT, true))
            addView(text(copy, 12.5f, TEXT), LinearLayout.LayoutParams(0, wrap, 1f).apply { marginStart = dp(8) })
        }
    }

    private fun control(copy: String, tone: Tone): View {
        if (copy == "toggle:on" || copy == "toggle:off") {
            return FrameLayout(this).apply {
                background = rounded(if (copy == "toggle:on") ACCENT else SURFACE_3, dp(999))
                addView(View(this@LaunchStateActivity).apply {
                    background = rounded(Color.WHITE, dp(999))
                }, FrameLayout.LayoutParams(dp(18), dp(18), if (copy == "toggle:on") Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL).apply {
                    leftMargin = dp(3)
                    rightMargin = dp(3)
                })
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(24))
            }
        }
        if (copy == ">") return text(">", 18f, MUTED, Typeface.BOLD)
        return pill(copy, when (tone) {
            Tone.WARN -> ORANGE
            Tone.DANGER -> RED
            else -> SURFACE_2
        }, tone != Tone.NORMAL)
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(0x0DFFFFFF)
        layoutParams = LinearLayout.LayoutParams(match, 1)
    }

    private fun verticalScroll(content: LinearLayout.() -> Unit): View {
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(14))
            content()
        }
        return ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(inner, FrameLayout.LayoutParams(match, wrap))
        }
    }

    private fun row(content: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            content()
        }
    }

    private fun circleText(copy: String, color: Int, sizeDp: Int, dark: Boolean = false): TextView {
        return TextView(this).apply {
            text = copy
            textSize = if (copy.length > 2) 9f else 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (dark) 0xFF071018.toInt() else TEXT)
            gravity = Gravity.CENTER
            background = rounded(color, dp(999))
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }
    }

    private fun pill(copy: String, color: Int, darkText: Boolean): TextView {
        return TextView(this).apply {
            text = copy
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (darkText) 0xFF071018.toInt() else TEXT)
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
            background = rounded(color, dp(999), stroke = 0x0DFFFFFF)
            minHeight = dp(24)
        }
    }

    private fun text(copy: String, size: Float, color: Int, style: Int = Typeface.NORMAL): TextView {
        return TextView(this).apply {
            text = copy
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(Typeface.DEFAULT, style)
            includeFontPadding = true
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }
    }

    private fun View.withMargins(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0
    ): View {
        layoutParams = LinearLayout.LayoutParams(match, wrap).apply {
            setMargins(dp(left), dp(top), dp(right), dp(bottom))
        }
        return this
    }

    private fun LinearLayout.addView(view: View, topMargin: Int) {
        addView(view, LinearLayout.LayoutParams(match, wrap).apply { this.topMargin = dp(topMargin) })
    }

    private fun shellBackground(): Drawable = object : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: Canvas) {
            val b = bounds
            paint.shader = LinearGradient(
                b.left.toFloat(),
                b.top.toFloat(),
                b.right.toFloat(),
                b.bottom.toFloat(),
                intArrayOf(0xFF111217.toInt(), BG, 0xFF07080A.toInt()),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(b, paint)

            paint.style = Paint.Style.FILL
            drawRadial(canvas, b.left + b.width() * 0.22f, b.top + b.height() * 0.12f, b.width() * 0.62f, 0x334ADE80)
            drawRadial(canvas, b.left + b.width() * 0.82f, b.top + b.height() * 0.22f, b.width() * 0.66f, 0x3A6CB4EE)
            drawRadial(canvas, b.left + b.width() * 0.68f, b.top + b.height() * 0.86f, b.width() * 0.68f, 0x2FA78BFA)

            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = 0x06FFFFFF
            val step = 46
            var x = b.left - b.height()
            while (x < b.right + b.height()) {
                canvas.drawLine(x.toFloat(), b.bottom.toFloat(), (x + b.height()).toFloat(), b.top.toFloat(), paint)
                x += step
            }
            paint.style = Paint.Style.FILL
        }

        private fun drawRadial(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
            paint.shader = RadialGradient(cx, cy, radius, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }

    private fun gradient(colors: IntArray, radius: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = radius.toFloat()
        }

    private fun gradientBg(colors: IntArray, radius: Int, stroke: Int = 0): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = radius.toFloat()
            if (stroke != 0) setStroke(1, stroke)
        }

    private fun cardBg(radius: Int, stroke: Int = 0x14FFFFFF): GradientDrawable =
        gradientBg(
            intArrayOf(
                0xFF252B34.toInt(),
                0xFF1A1D24.toInt(),
                0xFF121318.toInt()
            ),
            radius,
            stroke
        )

    private fun elevatedCardBg(radius: Int, stroke: Int = 0x18FFFFFF): GradientDrawable =
        gradientBg(
            intArrayOf(
                0xFF2B3744.toInt(),
                0xFF1D212A.toInt(),
                0xFF121318.toInt()
            ),
            radius,
            stroke
        )

    private fun quietCardBg(radius: Int, stroke: Int = 0x0DFFFFFF): GradientDrawable =
        gradientBg(
            intArrayOf(
                0xFF222730.toInt(),
                0xFF15171C.toInt()
            ),
            radius,
            stroke
        )

    private fun heroCardBg(radius: Int, stroke: Int = 0x3D6CB4EE): GradientDrawable =
        gradientBg(
            intArrayOf(
                0xFF17324A.toInt(),
                0xFF1A1B2A.toInt(),
                0xFF121318.toInt()
            ),
            radius,
            stroke
        )

    private fun rounded(color: Int, radius: Int, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (stroke != 0) setStroke(1, stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun resolveState(dataState: String): LaunchState = STATES[dataState] ?: STATES.getValue("hm-anonymous-ready")

    private enum class ScreenType { HOME, SETTINGS, SUBSCRIPTION, ACCOUNT, PRIVACY, PERMISSION, QUOTA }
    private enum class Tone { NORMAL, WARN, DANGER }

    private data class CardItem(
        val icon: String,
        val title: String,
        val body: String,
        val status: String,
        val tone: Tone = Tone.NORMAL
    )

    private data class LaunchState(
        val id: String,
        val dataState: String,
        val type: ScreenType,
        val topTitle: String,
        val topSubtitle: String,
        val heroTitle: String,
        val body: String,
        val primary: String,
        val secondary: String,
        val meter: Int,
        val walletLabel: String,
        val cards: List<CardItem>,
        val groups: List<Pair<String, List<CardItem>>> = emptyList(),
        val stageCaption: String = "",
        val stageTitle: String = "Quick Reply ready",
        val stageSubtitle: String = "Across WhatsApp, Instagram, Google Chat, Telegram",
        val sampleMessage: String = "Can you send the client-safe version before 6?",
        val sampleReply: String = "Sure. I will send the concise version before 6."
    )

    private val STATES: Map<String, LaunchState> = buildMap {
        homeStates().forEach { put(it.dataState, it) }
        settingsStates().forEach { put(it.dataState, it) }
        subscriptionStates().forEach { put(it.dataState, it) }
        accountStates().forEach { put(it.dataState, it) }
        privacyStates().forEach { put(it.dataState, it) }
        permissionStates().forEach { put(it.dataState, it) }
        quotaStates().forEach { put(it.dataState, it) }
    }

    private fun homeStates() = listOf(
        LaunchState("HM01", "hm-anonymous-ready", ScreenType.HOME, "WittyKeys", "Anonymous mode", "Use AI without opening another app.", "Start from Overlay for messages and screens, or Keyboard when you are typing.", "Use now", "Settings", 72, "18 credits", listOf(
            CardItem("OVR", "Open Overlay", "Ask about screens and reply across apps.", "Try now"),
            CardItem("QR", "Quick Reply", "Reply to WhatsApp, Instagram, Chat, and Telegram.", "Open"),
            CardItem("KBD", "Keyboard AI", "Rewrite, tone, grammar, translate, chat.", "Use")
        ), stageCaption = "Overlay and Keyboard work anonymously"),
        LaunchState("HM02", "hm-setup-recovery", ScreenType.HOME, "WittyKeys", "Anonymous mode", "Finish the one setup that matters.", "WittyKeys keeps available features active while you recover optional permissions.", "Continue setup", "Use available tools", 45, "Fix setup", listOf(
            CardItem("OVR", "Overlay permission", "Turn on the bubble above apps.", "Fix now", Tone.WARN),
            CardItem("KBD", "Keyboard ready", "Typing AI is available.", "Open"),
            CardItem("NLS", "Quick Replies paused", "Notification access is optional.", "Enable later")
        ), stageCaption = "Available tools stay open", stageTitle = "Permission recovery", stageSubtitle = "Finish optional setup"),
        LaunchState("HM03", "hm-quota-low", ScreenType.HOME, "WittyKeys", "Anonymous mode", "You have a few AI moves left.", "Spend credits on the moments that matter. Short replies cost less than screen AI.", "View options", "Keep using free tools", 18, "4 credits", listOf(
            CardItem("AI", "4 credits left", "Use short replies or upgrade for more.", "View usage", Tone.WARN),
            CardItem("QR", "Quick Reply", "Reply to WhatsApp, Instagram, Chat, and Telegram.", "Open"),
            CardItem("KBD", "Keyboard AI", "Rewrite, tone, grammar, translate, chat.", "Use")
        ), stageCaption = "Overlay and Keyboard work anonymously"),
        LaunchState("HM04", "hm-quota-empty", ScreenType.HOME, "WittyKeys", "Anonymous mode", "Free AI is paused for today.", "Overlay, Keyboard, settings, and history stay open. AI resumes after refill or upgrade.", "Upgrade", "Wait for refill", 0, "0 credits", listOf(
            CardItem("AI", "0 AI credits", "No hidden AI calls will run.", "Upgrade", Tone.DANGER),
            CardItem("OVR", "Overlay still works", "Open chats and drafts.", "Open"),
            CardItem("KBD", "Keyboard still works", "Type normally with local tools.", "Use")
        ), stageCaption = "Overlay and Keyboard work anonymously", sampleMessage = "AI is paused for today. Overlay and Keyboard stay usable.", sampleReply = "Upgrade or wait for refill"),
        LaunchState("HM05", "hm-paid-active", ScreenType.HOME, "WittyKeys", "Plus active", "You are set for heavier AI use.", "Plus gives you a larger credit pool and selected proactive conveniences.", "Start using Overlay", "Manage plan", 88, "Plus credits", listOf(
            CardItem("PLUS", "Plus wallet active", "Larger allowance for screen AI and chat.", "Use AI"),
            CardItem("QR", "Proactive replies", "Prepare suggestions when allowed.", "On"),
            CardItem("SCR", "Screen AI", "Ask about any screen faster.", "Capture")
        ), stageCaption = "Plus workspace", sampleMessage = "Prepare a sharper reply and keep screen AI available.", sampleReply = "Sure. I will send the concise version before 6."),
        LaunchState("HM06", "hm-backend-error", ScreenType.HOME, "WittyKeys", "Anonymous mode", "AI is resting. Your tools still work.", "No credits are spent while the backend is unavailable.", "Try again", "Open settings", 62, "Retry", listOf(
            CardItem("AI", "AI unavailable", "Credits are not spent during outage.", "Retry", Tone.WARN),
            CardItem("OVR", "Overlay sessions", "Read and copy existing drafts.", "Open"),
            CardItem("KBD", "Keyboard", "Keep typing without AI.", "Ready")
        ), stageCaption = "Available tools stay open", stageTitle = "AI paused", stageSubtitle = "No credits spent", sampleMessage = "Connection issue. Keep your drafts and retry later.", sampleReply = "Retry when AI reconnects")
    )

    private fun settingsStates(): List<LaunchState> {
        fun state(id: String, data: String, subtitle: String, title: String, body: String, groups: List<Pair<String, List<CardItem>>>) =
            LaunchState(id, data, ScreenType.SETTINGS, "Settings", subtitle, title, body, "Done", "Home", 70, "18 credits", emptyList(), groups)
        return listOf(
            state("ST01", "st-hub", "Control center", "Settings", "Everything launch-critical lives here: setup, AI, account, privacy, and support.", listOf(
                "Setup" to listOf(
                    CardItem("OVR", "Overlay", "Bubble, Ask AI, Quick Reply, permissions.", "Open"),
                    CardItem("KBD", "Keyboard", "Input method, languages, feedback, AI toolbar.", "Open"),
                    CardItem("PERM", "Permission recovery", "Fix skipped or missing setup.", "2 items", Tone.WARN)
                ),
                "AI and account" to listOf(
                    CardItem("AI", "AI usage", "Credits, costs, refill, quota reached.", "18 left"),
                    CardItem("PLUS", "Subscription", "Google Play billing and restore.", "Free"),
                    CardItem("ACCT", "Account", "Sign in only for plan and account controls.", "Anon")
                ),
                "Trust" to listOf(
                    CardItem("SAFE", "Privacy and permissions", "Sensitive access explained first.", "Open"),
                    CardItem("HELP", "Support", "Feedback, diagnostics, version.", "Open")
                )
            )),
            state("ST02", "st-overlay", "Overlay controls", "Overlay", "Manage the floating bubble, Quick Reply, Ask AI, and permission recovery.", listOf(
                "Overlay surface" to listOf(
                    CardItem("OVR", "Floating bubble", "Show WittyKeys above other apps.", "toggle:on"),
                    CardItem("POS", "Bubble position", "Remember last position after drag.", "Right side"),
                    CardItem("ASK", "Ask AI about screen", "Capture only after explicit tap.", "Manual")
                ),
                "Quick Reply" to listOf(
                    CardItem("QR", "Notification replies", "Use notification access for supported apps.", "toggle:on"),
                    CardItem("APP", "Supported apps", "WhatsApp, Instagram, Google Chat, Telegram.", ">"),
                    CardItem("A11Y", "Send helper", "Accessibility helper disclosure and setup.", "Optional")
                )
            )),
            state("ST03", "st-keyboard", "Keyboard controls", "Keyboard", "Keep the current keyboard behavior; apply theme and preference polish only.", listOf(
                "Input method" to listOf(
                    CardItem("KBD", "Enable WittyKeys", "Open Android keyboard settings.", "Ready"),
                    CardItem("DEF", "Default keyboard", "Switch current input method.", ">"),
                    CardItem("LAN", "Languages", "English, Hindi, Hinglish, layouts.", ">")
                ),
                "Typing behavior" to listOf(
                    CardItem("AI", "AI toolbar", "Reply, tone, grammar, translate, chat.", "toggle:on"),
                    CardItem("SUG", "Suggestions row", "Keep current predictions and emoji suggestions.", "toggle:on"),
                    CardItem("HAP", "Sound and vibration", "Keypress feedback preferences.", ">"),
                    CardItem("SIZE", "Keyboard height", "Use existing keyboard height controls.", "Normal")
                )
            )),
            state("ST04", "st-ai-usage", "AI wallet", "AI usage", "See balance, action costs, refill behavior, and Plus entry points.", listOf(
                "Balance" to listOf(
                    CardItem("AI", "Free credits", "Refreshes for anonymous use.", "18 left"),
                    CardItem("PLUS", "WittyKeys Plus", "More credits for heavier use.", ">")
                ),
                "Action costs" to listOf(
                    CardItem("1", "Short text actions", "Reply, grammar, tone, translate.", "1"),
                    CardItem("2", "Longer chat", "More context and longer output.", "2"),
                    CardItem("5", "Ask AI about screen", "Screenshot analysis costs more.", "3-5")
                )
            )),
            state("ST05", "st-privacy-permissions", "Privacy controls", "Privacy and permissions", "Review every sensitive permission before Android asks for it.", listOf(
                "Permissions" to listOf(
                    CardItem("OVR", "Draw over apps", "Required for floating overlay.", "Allowed"),
                    CardItem("NLS", "Notification access", "Optional for Quick Reply.", "Skipped"),
                    CardItem("SCR", "Screen capture", "On demand only.", "Manual"),
                    CardItem("A11Y", "Accessibility helper", "Optional with disclosure.", "Off")
                ),
                "Data controls" to listOf(
                    CardItem("LOG", "Sensitive analytics", "No message text, typed text, or screenshots.", "Protected"),
                    CardItem("DEL", "Clear local sessions", "Remove chat history from this device.", ">")
                )
            )),
            state("ST06", "st-support", "Help and version", "Support", "Get help, send feedback, and verify release information.", listOf(
                "Help" to listOf(
                    CardItem("HELP", "Contact support", "Send feedback without private message content.", ">"),
                    CardItem("BUG", "Report a problem", "Attach diagnostics after review.", ">"),
                    CardItem("DOC", "Privacy and terms", "Open public policy pages.", ">")
                ),
                "App" to listOf(
                    CardItem("VER", "Version", "Build 7.1 MVP launch candidate.", "7.1"),
                    CardItem("REL", "Release notes", "Internal test before production.", ">")
                )
            ))
        )
    }

    private fun subscriptionStates() = listOf(
        app("SB01", "sb-plus-offer", ScreenType.SUBSCRIPTION, "WittyKeys Plus", "Google Play billing", "Use more AI when WittyKeys becomes daily.", "Free stays useful. Plus exists for heavier AI users so the product remains sustainable.", "Upgrade with Play", "Restore purchase", 30, cards("PLUS:Larger allowance:Higher credit pool for chat, quick reply, and screen AI.:Plus", "AUTO:Convenience automation:Selected proactive reply prep when allowed.:Included", "SAFE:No unlimited promise:Usage remains protected by credit limits.:Clear")),
        app("SB02", "sb-restore", ScreenType.SUBSCRIPTION, "WittyKeys Plus", "Google Play billing", "Restore your purchase.", "We verify with Play and backend before unlocking paid state.", "Restore", "Not now", 30, cards("PLAY:Play Billing:Restore checks your Google Play purchase state.:Secure", "SYNC:Backend sync:The server verifies entitlement before unlock.:Required")),
        app("SB03", "sb-active", ScreenType.SUBSCRIPTION, "WittyKeys Plus", "Google Play billing", "Plus is active.", "Your verified Play entitlement has unlocked a larger allowance.", "Manage plan", "Back", 92, cards("PLUS:Active plan:WittyKeys Plus is verified.:Active", "AI:Credit pool:Paid allowance is available for this period.:Healthy")),
        app("SB04", "sb-expired", ScreenType.SUBSCRIPTION, "WittyKeys Plus", "Google Play billing", "Your plan expired.", "Free use continues. Renew only if you need the larger allowance.", "Renew", "Use free tier", 12, cards("PLUS:Expired:Play Billing no longer reports an active entitlement.:Expired:warn", "FREE:Free tier:Non-AI features remain available.:Available")),
        app("SB05", "sb-billing-error", ScreenType.SUBSCRIPTION, "WittyKeys Plus", "Google Play billing", "Billing needs attention.", "Update your payment method in Google Play to keep Plus benefits.", "Open Play", "Back", 12, cards("PAY:Payment issue:Resolve in Play subscriptions.:Action:warn", "FREE:Fallback:Free tier remains usable.:Available")),
        app("SB06", "sb-quota-upgrade", ScreenType.SUBSCRIPTION, "WittyKeys Plus", "Google Play billing", "Get more AI room today.", "Upgrade appears after real usage, when the value is already clear.", "Upgrade", "Wait for refill", 0, cards("AI:Credits empty:AI actions pause until refill or upgrade.:Empty:danger", "PLUS:Plus credits:Higher allowance for frequent AI use.:Upgrade"))
    )

    private fun accountStates() = listOf(
        app("ACCT01", "acct-signin-reason", ScreenType.ACCOUNT, "Account", "Anonymous first", "WittyKeys works anonymously. Sign in for subscription and account controls.", "Overlay and Keyboard remain usable without login.", "Continue", "Use anonymously", 70, cards("WHY:Why sign in?:Restore purchases and manage account deletion.:Clear", "ANON:Anonymous use:Overlay and Keyboard remain usable without login.:Allowed")),
        app("ACCT02", "acct-auth-options", ScreenType.ACCOUNT, "Account", "Anonymous first", "Choose sign-in method", "Use Google or phone only for account management.", "Google", "Phone number", 70, cards("G:Google:Fast sign-in for subscription restore.:Option", "SMS:Phone:Use verified phone sign-in.:Option")),
        app("ACCT03", "acct-profile-anonymous", ScreenType.ACCOUNT, "Account", "Anonymous first", "Anonymous profile", "You are using WittyKeys without an account.", "Sign in", "Back", 56, cards("FREE:Free AI credits:Daily credits are tied to this app instance.:Active", "DEL:Local data:Clear local sessions and preferences.:Control")),
        app("ACCT04", "acct-profile-signed-in", ScreenType.ACCOUNT, "Account", "Anonymous first", "Account", "Manage plan, usage, privacy, and deletion.", "Manage plan", "Logout", 82, cards("USER:Signed in:Account controls are available.:Ready", "PLUS:Subscription:Current Play entitlement is verified by backend.:Synced", "DATA:Data controls:Delete account or clear local data.:Open")),
        app("ACCT05", "acct-delete-account", ScreenType.ACCOUNT, "Account", "Anonymous first", "Delete account", "This removes account data and clears local account state where policy allows.", "Delete account", "Cancel", 82, cards("WARN:Permanent action:Subscription records follow Play and legal retention rules.:Careful:danger", "LOCAL:Local cleanup:Sessions and identifiers are cleared from this device.:Included")),
        app("ACCT06", "acct-logout", ScreenType.ACCOUNT, "Account", "Anonymous first", "Log out?", "WittyKeys will continue anonymously after logout.", "Log out", "Cancel", 70, cards("ANON:Anonymous mode:Overlay and Keyboard remain usable.:Continues", "SYNC:Subscription:Sign in again to manage paid plan.:Needed"))
    )

    private fun privacyStates() = listOf(
        app("PR01", "pr-privacy-summary", ScreenType.PRIVACY, "Privacy", "Plain language", "Privacy summary", "WittyKeys explains every sensitive permission before Android asks.", "Data controls", "Back", 70, cards("LOG:No message logging:Prompts, replies, notification text, and screenshots are not analytics payloads.:Rule", "CAP:Screen AI:Screenshots are sent only after you tap capture.:Manual", "NLS:Quick Replies:Notification access is optional.:Optional")),
        app("PR02", "pr-terms", ScreenType.PRIVACY, "Privacy", "Plain language", "Terms of use", "Plain launch terms live in app and on the website.", "Open website", "Back", 70, cards("USE:Responsible use:AI suggestions are drafts you control.:Read", "BILL:Billing:Subscriptions are managed through Google Play.:Read")),
        app("PR03", "pr-data-controls", ScreenType.PRIVACY, "Privacy", "Plain language", "Data controls", "Clear local data, manage account, or review permissions.", "Open controls", "Back", 70, cards("LOCAL:Local sessions:Clear chat/session cache on this device.:Control", "ACCT:Account deletion:Signed-in users can delete account in app.:Control", "PERM:Permissions:Open Android permission recovery.:Control"))
    )

    private fun permissionStates() = listOf(
        app("PM01", "pm-keyboard-missing", ScreenType.PERMISSION, "Permission", "You choose", "Keyboard setup needed", "WittyKeys needs keyboard access only if you want AI tools inside text fields. Android will show the input method selector next.", "Enable keyboard", "Skip keyboard", 35, cards("KBD:Input method:Lets WittyKeys appear as a keyboard when you select it.:Required", "SAFE:Choice:Overlay still works if keyboard is skipped.:Flexible")),
        app("PM02", "pm-overlay-missing", ScreenType.PERMISSION, "Permission", "You choose", "Overlay permission needed", "WittyKeys needs draw-over-apps permission to show the floating bubble and expanded overlay above other apps.", "Enable overlay", "Skip overlay", 35, cards("OVR:Draw over apps:Shows the bubble and expanded overlay while you use another app.:Required", "KBD:Keyboard:Keyboard AI still works if skipped.:Available")),
        app("PM03", "pm-accessibility-missing", ScreenType.PERMISSION, "Permission", "You choose", "Accessibility helper", "WittyKeys asks for Accessibility only if you want assisted overlay placement or reply actions. It is optional and can be skipped.", "Open settings", "Skip for now", 35, cards("A11Y:Prominent disclosure:Shown before Android settings open.:Required", "SKIP:Optional path:Core onboarding can continue.:Allowed")),
        app("PM04", "pm-nls-optional", ScreenType.PERMISSION, "Permission", "You choose", "Quick Replies permission", "WittyKeys needs notification access only to find message notifications from supported apps and prepare reply suggestions.", "Enable", "Skip", 35, cards("NLS:Notification listener:Optional for Quick Replies from supported messaging apps.:Optional", "SAFE:No message analytics:Message text is not logged.:Protected")),
        app("PM05", "pm-screen-capture", ScreenType.PERMISSION, "Permission", "You choose", "Screen capture disclosure", "WittyKeys uses screen capture only after you tap Ask AI about this screen, so the current screen can be analyzed.", "Start capture", "Cancel", 35, cards("SCR:On demand:No background screen capture or silent capture.:Manual", "AI:Credit check:Credits are checked before analysis.:Before call"))
    )

    private fun quotaStates() = listOf(
        app("QT01", "qt-free-balance", ScreenType.QUOTA, "AI Wallet", "Cost-aware AI", "Free AI credits", "Use AI intentionally without sign-in.", "Use AI", "How credits work", 72, cards("1:Short actions:Reply, grammar, tone, translate.:1 credit", "2:Longer chat:More context costs more.:2 credits", "5:Screen AI:Image analysis uses more credits.:3-5")),
        app("QT02", "qt-low-balance", ScreenType.QUOTA, "AI Wallet", "Cost-aware AI", "Credits running low", "Choose shorter actions or upgrade for more.", "Upgrade", "Continue free", 18, cards("AI:Low balance:4 credits left today.:Low:warn", "SAVE:Cost-aware:Screen AI uses more than short replies.:Tip")),
        app("QT03", "qt-empty-balance", ScreenType.QUOTA, "AI Wallet", "Cost-aware AI", "Credits used today", "No hidden AI calls will run. Free refill returns later.", "Upgrade", "Wait for refill", 0, cards("AI:0 credits:AI actions are paused.:Empty:danger", "FREE:Still useful:Keyboard, overlay, history, and settings still work.:Open")),
        app("QT04", "qt-plus-balance", ScreenType.QUOTA, "AI Wallet", "Cost-aware AI", "Plus credits active", "Your paid allowance supports heavier AI use.", "Use AI", "Manage plan", 88, cards("PLUS:Larger pool:Paid credits are available this period.:Active", "AUTO:Convenience:Selected proactive features can run when allowed.:Included")),
        app("QT05", "qt-action-cost", ScreenType.QUOTA, "AI Wallet", "Cost-aware AI", "Action costs", "Credits map to backend cost so free use stays sustainable.", "Got it", "Back", 55, cards("1:Quick Reply:Short text generation.:1", "2:Long chat:More context and output.:2", "5:Ask AI about screen:Screenshot analysis.:3-5"))
    )

    private fun app(
        id: String,
        data: String,
        type: ScreenType,
        top: String,
        subtitle: String,
        hero: String,
        body: String,
        primary: String,
        secondary: String,
        meter: Int,
        cards: List<CardItem>
    ) = LaunchState(id, data, type, top, subtitle, hero, body, primary, secondary, meter, if (meter <= 0) "0 credits" else "$meter%", cards)

    private fun cards(vararg rows: String): List<CardItem> = rows.map { encoded ->
        val parts = encoded.split(":")
        CardItem(
            icon = parts.getOrElse(0) { "" },
            title = parts.getOrElse(1) { "" },
            body = parts.getOrElse(2) { "" },
            status = parts.getOrElse(3) { "" },
            tone = when (parts.getOrElse(4) { "" }) {
                "warn" -> Tone.WARN
                "danger" -> Tone.DANGER
                else -> Tone.NORMAL
            }
        )
    }
}
