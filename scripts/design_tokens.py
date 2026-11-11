"""Project the library's static :root/.dark custom properties without executing CSS.

Only the library's token-authoring syntax is supported. This is not a browser cascade
engine: unresolved variables, cycles and unsupported nested token blocks fail closed.
"""
import hashlib
import re

COLOR_GROUPS = ['bg', 'text', 'border', 'primary', 'accent', 'interaction', 'tone',
                'status', 'lifecycle', 'verdict', 'success', 'warning', 'danger',
                'info', 'chart', 'viz', 'code']
VARIABLE = re.compile(r'var\(\s*(--[\w-]+)\s*\)')


def css_modes(text):
    clean = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    modes = {}
    for selector in [':root', '.dark']:
        values = {}
        blocks = list(re.finditer(re.escape(selector) + r'\s*\{([^{}]*)\}', clean))
        if not blocks:
            raise ValueError(f'Missing static token block: {selector}')
        for block in blocks:
            preceding = clean[:block.start()]
            if preceding.count('{') != preceding.count('}'):
                raise ValueError('Conditional or nested token blocks are unsupported')
            for declaration in block.group(1).split(';'):
                if not declaration.strip():
                    continue
                match = re.fullmatch(r'\s*(--[\w-]+)\s*:\s*(.*?)\s*', declaration, re.S)
                if not match or not match[2]:
                    raise ValueError(f'Unsupported token declaration in {selector}')
                values[match[1]] = match[2]
        modes[selector] = values
    return modes[':root'], {**modes[':root'], **modes['.dark']}


def resolved(name, values, seen=()):
    if name not in values:
        raise ValueError(f'Undefined CSS token: {name}')
    if name in seen:
        raise ValueError(f'CSS alias cycle: {name}')
    value = VARIABLE.sub(lambda match: resolved(match[1], values, (*seen, name)), values[name])
    if 'var(' in value:
        raise ValueError(f'Unsupported CSS variable expression: {name}')
    return value.strip()


def project(text):
    light, dark = css_modes(text)

    def group(prefix):
        return {name[len(prefix):]: resolved(name, light)
                for name in light if name.startswith(prefix)}

    colors = {name: {} for name in COLOR_GROUPS}
    for name in light:
        if not name.startswith('--sh-'):
            continue
        suffix = name[len('--sh-'):]
        category = next((key for key in COLOR_GROUPS if suffix.startswith(key + '-')), None)
        if name == '--sh-switch-track':
            category = 'bg'
        if not category:
            continue
        value = resolved(name, light)
        if not re.fullmatch(r'#[0-9a-fA-F]{6}', value):
            # --sh-text-* contains both colors and typography dimensions.
            if category == 'text' and (name + '-lh' in light or name.endswith('-lh')):
                continue
            raise ValueError(f'Unsupported color token: {name}')
        night = resolved(name, dark)
        if not re.fullmatch(r'#[0-9a-fA-F]{6}', night):
            raise ValueError(f'Unsupported dark color token: {name}')
        colors[category][name[2:]] = {'light': value.lower(), 'dark': night.lower()}
    scale = []
    for name in light:
        if name.startswith('--sh-text-') and name + '-lh' in light:
            scale.append({'name': name[len('--sh-text-'):], 'token': name,
                          'size': resolved(name, light), 'lineHeight': resolved(name + '-lh', light)})
    sizes = {'icons': group('--sh-icon-'), **group('--sh-size-')}
    for key, variable in [('focusRingWidth', '--sh-focus-ring-width'),
                          ('focusRingOffset', '--sh-focus-ring-offset')]:
        if variable in light:
            sizes[key] = resolved(variable, light)
    controls = {}
    for name in light:
        if name.startswith('--sh-control-'):
            size, property_name = name[len('--sh-control-'):].split('-', 1)
            controls.setdefault(size, {})[property_name] = resolved(name, light)
    shadows = {name[len('--shadow-'):]: {'light': resolved(name, light), 'dark': resolved(name, dark)}
               for name in light if name.startswith('--shadow-')}
    aliases = {name: VARIABLE.fullmatch(value)[1] for name, value in light.items()
               if name.startswith('--sh-') and VARIABLE.fullmatch(value)}
    # Resolve every alias, including those not selected by a particular UI task.
    for name in aliases:
        resolved(name, light); resolved(name, dark)
    opacity = {name: resolved('--sh-overlay-' + name + '-alpha', light)
               for name in ['scrim', 'glass'] if '--sh-overlay-' + name + '-alpha' in light}
    return {'color': {key: value for key, value in colors.items() if value},
            'typography': {'families': group('--sh-font-'), 'scale': scale,
                           'weights': group('--sh-weight-'), 'tracking': group('--sh-tracking-')},
            'spacing': group('--sh-space-'), 'radius': group('--sh-radius-'),
            'size': sizes, 'control': controls, 'shadow': shadows,
            'motion': {'duration': group('--sh-duration-'), 'easing': group('--sh-ease-')},
            'zIndex': group('--sh-z-'), 'opacity': opacity, 'aliases': aliases}


def differences(actual, expected, prefix='tokens'):
    if isinstance(expected, dict) and isinstance(actual, dict):
        errors = []
        for key in sorted(set(actual) | set(expected)):
            path = f'{prefix}.{key}'
            if key not in actual:
                errors.append(f'{path}: missing')
            elif key not in expected:
                errors.append(f'{path}: no longer defined by CSS')
            else:
                errors.extend(differences(actual[key], expected[key], path))
        return errors
    return [] if actual == expected else [f'{prefix}: {actual!r} != CSS {expected!r}']


def source_hash(data):
    return hashlib.sha256(data).hexdigest()
