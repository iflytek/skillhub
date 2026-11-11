"""Bounded model-facing pages; emitting a page is not proof of delivery."""
import hashlib
import json

DEFAULT_LIMIT = 1800
MAX_LIMIT = 2400
MAX_OUTPUT_BYTES = 6500


def digest(text):
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def page(payload, offset=0, limit=DEFAULT_LIMIT, expected=None):
    if not isinstance(offset, int) or not isinstance(limit, int) or offset < 0 or not 1 <= limit <= MAX_LIMIT:
        raise ValueError(f'offset must be nonnegative; limit must be 1..{MAX_LIMIT}')
    identity = digest(payload)
    if expected is not None and expected != identity:
        raise ValueError('Source/query changed; restart at offset 0 and reassess previous evidence')
    if offset and expected is None:
        raise ValueError('Continuation requires --expect-sha256 from the first page')
    if offset >= len(payload) and not (offset == 0 and not payload):
        raise ValueError('offset is outside the payload')
    end = min(len(payload), offset + limit)
    while True:
        body = payload[offset:end]
        result = {'chunk': {'offset': offset, 'nextOffset': end if end < len(payload) else None,
                            'totalCharacters': len(payload), 'payloadSha256': identity,
                            'wholePayload': offset == 0 and end == len(payload)},
                  'text': body, 'pageSha256': digest(body),
                  'instruction': 'Read this page in a separate tool result. Follow nextOffset with '
                                 '--expect-sha256. Missing/truncated pages are unresolved; '
                                 'an emitted page or final page alone is not complete delivery.'}
        if len(json.dumps(result, ensure_ascii=False).encode('utf-8')) <= MAX_OUTPUT_BYTES:
            return result
        if end <= offset + 1:
            raise ValueError('Cannot fit one character within page budget')
        end = offset + max(1, (end - offset) * 3 // 4)


def tool_texts(value):
    """Unwrap complete tool JSON envelopes, never user/assistant messages."""
    if isinstance(value, str):
        yield value
        try:
            decoded = json.loads(value)
        except ValueError:
            # Codex adds a short display prefix before complete JSON results.
            for start in ('{\"chunk\"', '{\n  \"chunk\"', '{\"chunk_id\"'):
                position = value.find(start)
                if position >= 0:
                    try:
                        decoded, _ = json.JSONDecoder().raw_decode(value[position:])
                        yield from tool_texts(decoded)
                    except ValueError:
                        pass
            return
        if not isinstance(decoded, str):
            yield from tool_texts(decoded)
    elif isinstance(value, list):
        for item in value:
            yield from tool_texts(item)
    elif isinstance(value, dict):
        for item in value.values():
            yield from tool_texts(item)


def transcript_outputs(path, after_line=0):
    rows = []
    epoch = after_line
    with path.open(encoding='utf-8') as stream:
        for number, line in enumerate(stream, 1):
            item = json.loads(line)
            if item.get('type') == 'compacted':
                epoch = max(epoch, number)
            payload = item.get('payload', {})
            if item.get('type') == 'response_item' and payload.get('type') in (
                    'custom_tool_call_output', 'function_call_output'):
                rows.append((number, payload.get('output')))
    return epoch, [(number, text) for number, output in rows if number > epoch
                   for text in tool_texts(output)]


def coverage(payload, outputs):
    """Match actual current text or fully decodable matching pages, with no gaps."""
    identity = digest(payload)
    encoded = json.dumps(payload, ensure_ascii=False)[1:-1]
    for line, text in outputs:
        if payload and (payload in text or encoded in text):
            return {'complete': True, 'sha256': identity, 'lines': [line], 'missing': []}
    spans = []
    for line, text in outputs:
        try:
            item = json.loads(text)
            chunk = item['chunk']
            body = item['text']
            begin = chunk['offset']
            end = begin + len(body)
            if (isinstance(begin, bool) or not isinstance(begin, int) or begin < 0
                    or end > len(payload) or chunk['totalCharacters'] != len(payload)
                    or chunk['payloadSha256'] != identity or payload[begin:end] != body
                    or item.get('pageSha256') != digest(body)
                    or chunk['nextOffset'] != (end if end < len(payload) else None)):
                continue
            spans.append((begin, end, line))
        except (ValueError, KeyError, TypeError):
            continue
    cursor = 0
    missing = []
    for begin, end, _ in sorted(spans):
        if begin > cursor:
            missing.append([cursor, begin])
        cursor = max(cursor, end)
    if cursor < len(payload):
        missing.append([cursor, len(payload)])
    return {'complete': not missing and bool(spans), 'sha256': identity,
            'lines': sorted({line for _, _, line in spans}), 'missing': missing}
