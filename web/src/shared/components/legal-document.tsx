import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'

interface LegalSection {
  title: string
  paragraphs: readonly string[]
  bullets?: readonly string[]
}

interface LegalDocumentProps {
  eyebrow: string
  title: string
  summary: string
  lastUpdated: string
  note?: string
  sections: readonly LegalSection[]
}

export function LegalDocument({
  eyebrow,
  title,
  summary,
  lastUpdated,
  note,
  sections,
}: LegalDocumentProps) {
  return (
    <div className="mx-auto max-w-4xl space-y-8 animate-fade-up">
      <div className="space-y-4">
        <div className="inline-flex items-center rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-primary">
          {eyebrow}
        </div>
        <div className="space-y-3">
          <h1 className="text-4xl font-bold font-heading text-foreground md:text-5xl">{title}</h1>
          <p className="max-w-3xl text-lg leading-relaxed text-muted-foreground">{summary}</p>
          <p className="text-sm text-muted-foreground">{lastUpdated}</p>
        </div>
        {note ? (
          <div className="rounded-2xl border border-border/60 bg-card/60 px-5 py-4 text-sm leading-6 text-muted-foreground">
            {note}
          </div>
        ) : null}
      </div>

      <div className="space-y-4">
        {sections.map((section) => (
          <Card key={section.title} className="glass-strong">
            <CardHeader>
              <CardTitle className="text-xl font-heading">{section.title}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {section.paragraphs.map((paragraph) => (
                <p key={paragraph} className="text-sm leading-7 text-muted-foreground">
                  {paragraph}
                </p>
              ))}
              {section.bullets ? (
                <ul className="list-disc space-y-2 pl-5 text-sm leading-7 text-muted-foreground">
                  {section.bullets.map((bullet) => (
                    <li key={bullet}>{bullet}</li>
                  ))}
                </ul>
              ) : null}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
