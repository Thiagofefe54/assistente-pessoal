"""Avoid concurrent paid report preparation in the single-process deployment."""
from functools import wraps
from inspect import signature
from threading import Lock, RLock
from fastapi import HTTPException

_mutex = Lock()
_owners = {}


def guard_preparation(flag):
    def decorate(function):
        parameters = signature(function)

        @wraps(function)
        def guarded(*args, **kwargs):
            bound = parameters.bind(*args, **kwargs)
            bound.apply_defaults()
            if not bound.arguments[flag]:
                return function(*args, **kwargs)
            owner = str(bound.arguments['owner'])
            with _mutex:
                entry = _owners.setdefault(owner, [RLock(), 0])
                entry[1] += 1
            acquired = entry[0].acquire(blocking=False)
            try:
                if not acquired:
                    raise HTTPException(409, 'Já estou preparando um relatório. Aguarde e consulte o resultado antes de pedir outro.')
                # Reentrant: a period may prepare one child chapter on this thread.
                return function(*args, **kwargs)
            finally:
                if acquired:
                    entry[0].release()
                with _mutex:
                    entry[1] -= 1
                    if not entry[1]:
                        del _owners[owner]
        return guarded
    return decorate
