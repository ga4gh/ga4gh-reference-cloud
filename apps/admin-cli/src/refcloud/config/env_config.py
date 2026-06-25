import os

class EnvConfig:
    _instance = None

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._load_config()
        return cls._instance
    
    def _load_config(self):
        env_keys_defaults = [
            ('POSTGRES_HOST', '127.0.0.1'),
            ('POSTGRES_PORT', '5432'),
            ('POSTGRES_DB', 'refcloudapi'),
            ('POSTGRES_USER', 'refcloudapi'),
            ('POSTGRES_PASSWORD', 'secret'),
        ]

        for key, default in env_keys_defaults:
            value = os.environ.get(key, default)
            setattr(self, key, value)