from os import environ

from django.conf import settings
from users.choices import TokenVersionChoices
from users.models import Token, User


su_name = environ.get("SUPERUSER_NAME", "admin")
su_email = environ.get("SUPERUSER_EMAIL", "admin@example.com")
su_password = environ.get("SUPERUSER_PASSWORD", "admin")
su_api_key = environ.get("SUPERUSER_API_KEY")
su_api_token = environ.get("SUPERUSER_API_TOKEN")

if User.objects.filter(username=su_name).exists():
    print(f'User with name "{su_name}" already exists.')
else:
    user = User.objects.create_superuser(su_name, su_email, su_password)
    if not settings.API_TOKEN_PEPPERS:
        print("⚠️ No API token was created as API_TOKEN_PEPPERS is not set")
    elif su_api_key and su_api_token:
        token = Token.objects.create(
            user=user,
            token=su_api_token,
            version=TokenVersionChoices.V2,
            key=su_api_key,
        )
        print(f"💡 API token created: {token.get_auth_header_prefix()}<Your token>")
    else:
        print("⚠️ No API token was created: SUPERUSER_API_KEY and SUPERUSER_API_TOKEN are required")
