#!/usr/bin/env python3
"""Import the official Telegram X profile and add Frogram's navigation hot paths.

Fetch the desired official revision first. The generated profile contains method
descriptors only; it never requires an account snapshot or session credentials.
"""
import argparse
from pathlib import Path
import subprocess

UPSTREAM = "d132af883b8408760f1ff716021fa20ff86f392a"
SOURCE = "app/src/main/generated/baselineProfiles/baseline-prof.txt"
EXTRA = """
HSPLorg/thunderdog/challegram/ui/MessagesController;->createMessagesView(Landroid/content/Context;)Landroid/view/View;
HPLorg/thunderdog/challegram/ui/ChatsController;->openChatImpl(Lorg/drinkless/tdlib/TdApi$Chat;)V
HPLorg/thunderdog/challegram/navigation/NavigationController;->animateNavigateImpl(Lorg/thunderdog/challegram/navigation/ViewController;IZIZ)V
HPLorg/thunderdog/challegram/navigation/GlassNavigationTransition;->onPreDraw()Z
HPLorg/thunderdog/challegram/navigation/GlassNavigationTransition;->onDraw(Landroid/graphics/Canvas;)V
HPLorg/thunderdog/challegram/navigation/GlassContentSnapshot;->capture(Landroid/view/View;I)Z
HPLorg/thunderdog/challegram/navigation/GlassContentSnapshot;->captureContents(Landroid/view/View;I)Z
HPLorg/thunderdog/challegram/widget/ChatGlassDrawable;->prepareBackdrop()Z
HPLorg/thunderdog/challegram/widget/ChatGlassDrawable;->prepareBackdropContents()Z
HPLorg/thunderdog/challegram/util/AlphaBlur;->blur([III)V
HPLorg/thunderdog/challegram/util/GlassBlur;->blur([III)V
""".strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--revision", default=UPSTREAM)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    revision = subprocess.check_output(
        ["git", "rev-parse", "--verify", args.revision + "^{commit}"], cwd=repo, text=True
    ).strip()
    profile = subprocess.check_output(
        ["git", "show", revision + ":" + SOURCE], cwd=repo
    ).decode("utf-8").strip()
    header = (
        "# Telegram X baseline profile (GPL-3.0), with Frogram navigation additions.\n"
        "# Source: https://github.com/TGX-Android/Telegram-X/commit/" + revision + "\n"
        "# Regenerate with: python -B scripts/update-baseline-profile.py --revision " + revision + "\n"
    )
    target = repo / "app/src/main/baseline-prof.txt"
    target.write_text(header + profile + "\n\n# Frogram glass navigation\n" + EXTRA + "\n", encoding="utf-8")
    print(f"Wrote {len(profile.splitlines())} upstream rules and {len(EXTRA.splitlines())} Frogram rules")


if __name__ == "__main__":
    main()
