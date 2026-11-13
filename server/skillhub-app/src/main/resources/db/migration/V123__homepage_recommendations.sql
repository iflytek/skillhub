-- Draft and published snapshots share one optimistic-lock version.
INSERT INTO system_setting(setting_key, value_json) VALUES ('site.recommendations', '{}'::jsonb)
ON CONFLICT (setting_key) DO NOTHING;

-- Ordinary filter labels, not platform-curation privileges. Preserve existing definitions and translations.
INSERT INTO label_definition(slug, type, visible_in_filter, sort_order)
SELECT seed.slug, 'RECOMMENDED', TRUE, seed.position
FROM (VALUES ('productivity', 10), ('development', 20), ('learning', 30), ('writing', 40), ('research', 50), ('security', 60)) seed(slug, position)
WHERE NOT EXISTS (SELECT 1 FROM label_definition d WHERE lower(d.slug) = seed.slug);
INSERT INTO label_translation(label_id, locale, display_name)
SELECT d.id, t.locale, t.name FROM label_definition d JOIN
(VALUES ('productivity','zh','办公效率'),('development','zh','研发'),('learning','zh','学习'),('writing','zh','内容写作'),('research','zh','资料研究'),('security','zh','安全检查'),
('productivity','en','Productivity'),('development','en','Development'),('learning','en','Learning'),('writing','en','Writing'),('research','en','Research'),('security','en','Security')) t(slug,locale,name)
ON d.slug=t.slug AND d.type='RECOMMENDED'
ON CONFLICT (label_id,locale) DO NOTHING;
-- Only previously unlabeled, exact builtin-owned global skills receive one initial label.
-- This migration runs once; a later manual removal remains removed.
INSERT INTO skill_label(skill_id,label_id,created_by)
SELECT s.id,d.id,s.owner_id FROM skill s JOIN namespace n ON n.id=s.namespace_id
JOIN (VALUES
('meeting-note-summarizer','productivity'),('daily-standup-journal','productivity'),('time-blocking-scheduler','productivity'),
('frontend-design','development'),('diagram-maker','development'),('documentation-writer','development'),
('study-strategy-selector','learning'),('retrieval-practice-generator','learning'),('exam-ready','learning'),
('storytelling-advisor','writing'),('linkedin-post-formatter','writing'),('zero-slop','writing'),
('cue-omni-reader','research'),('ai-claim-checker','research'),('decision-matrix','research'),
('agentguard','security'),('plugin-scanner','security')) t(slug,label) ON s.slug=t.slug
JOIN label_definition d ON d.slug=t.label AND d.type='RECOMMENDED'
WHERE n.slug='global' AND s.owner_id='builtin-skill-publisher'
AND NOT EXISTS (SELECT 1 FROM skill_label sl WHERE sl.skill_id=s.id)
ON CONFLICT (skill_id,label_id) DO NOTHING;
