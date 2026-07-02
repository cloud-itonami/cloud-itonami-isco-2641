# cloud-itonami-isco-2641

Open Occupation Blueprint for **ISCO-08 2641**: Authors and Related Writers.

This repository designs a forkable OSS business for an independent author /
novelist (小説家): serialized fiction and commissioned writing where the writer
keeps their own manuscripts, worldview bibles, serialization schedule and
rights records instead of renting a closed publishing SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a print-and-fulfilment robot performs physical
proof printing, binding and shipment handling under an actor that proposes
actions and an independent **Writing Governor** that gates them. The governor
never dispatches hardware itself; `:high`/`:safety-critical` actions (such as
publishing-rights overrides, plagiarism-flag overrides, or disclosing an
unreleased manuscript) require human sign-off.

A live sample of the operator console (robotics safety console, shared
template) is rendered in
[docs/samples/operator-console.html](docs/samples/operator-console.html) —
pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
manuscript brief + worldview bible + serialization schedule
        |
        v
Writing Advisor -> Writing Governor -> draft/serialize/publish, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose an unreleased manuscript without governor
approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `2641`). Required capabilities:

- :robotics
- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

Craft library (public, kotoba-lang):
[`shousetsu`](https://github.com/kotoba-lang/shousetsu) — work-agnostic
serialized web-fiction vocabulary (author/work/episode entity scheme, datom
ops, body-as-blob invariant). The private reference implementation is
gftdcojp's `ai-gftd-syosetsuka` actor (ADR-2607023000: コードは kotoba-lang、
職能は cloud-itonami-isco、商売は gftdcojp).

## Reference actor (`:maturity :implemented`)

Full itonami Actor pattern (like
[`cloud-itonami-isco-6130`](https://github.com/cloud-itonami/cloud-itonami-isco-6130) /
[`-2652`](https://github.com/cloud-itonami/cloud-itonami-isco-2652)): a real
[`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph` with Advisor and Governor as distinct nodes and human-in-the-loop
interrupt/resume. The governor enforces the
[`kotoba-lang/shousetsu`](https://github.com/kotoba-lang/shousetsu)
**body-as-blob invariant as a HARD invariant** (an inline `:body` is
structurally refused — long-form text never becomes a datom), and episode
commits carry shousetsu-built entity ids and datom ops
(`episode-meta->ops` → `:ep/bodyBlobKey`).

- HARD → `:hold`: unregistered work, non-`:propose` effect, inline body.
- ESCALATE → `:request-approval` (human-signed): reprint licensing, publish
  without an uploaded body blob, low confidence.

```bash
clojure -M:test
```

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
