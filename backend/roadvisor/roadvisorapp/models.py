from django.db import models
from django.contrib.auth.models import AbstractBaseUser, BaseUserManager, Group, Permission, PermissionsMixin
import uuid


class CustomUserManager(BaseUserManager):
    def create_user(self, email, password=None, **extra_fields):
        if not email:
            raise ValueError('The Email field must be set')
        email = self.normalize_email(email)
        user = self.model(email=email, **extra_fields)
        user.set_password(password)
        user.save()
        return user

    def create_superuser(self, email, password=None, **extra_fields):
        extra_fields.setdefault('is_staff', True)
        extra_fields.setdefault('is_superuser', True)

        if extra_fields.get('is_staff') is not True:
            raise ValueError('Superuser must have is_staff=True.')
        if extra_fields.get('is_superuser') is not True:
            raise ValueError('Superuser must have is_superuser=True.')

        return self.create_user(email, password, **extra_fields)


class CustomUser(AbstractBaseUser, PermissionsMixin):
    email = models.EmailField(unique=True, help_text="Enter a valid email address.")
    first_name = models.CharField(max_length=30, help_text="Enter your first name.")
    last_name = models.CharField(max_length=30, help_text="Enter your last name.")
    is_active = models.BooleanField(default=False, help_text="Designates whether this user should be treated as active. Unselect this instead of deleting accounts.")
    is_staff = models.BooleanField(default=False, help_text="Designates whether the user can log into this admin site.")
    date_joined = models.DateTimeField(auto_now_add=True)
    token = models.UUIDField(default=uuid.uuid4, editable=False, unique=True, help_text="Unique identifier for the user.", db_index=True)

    USERNAME_FIELD = 'email'
    REQUIRED_FIELDS = ['first_name', 'last_name']

    objects = CustomUserManager()

    class Meta:
        db_table = 'custom_user'

    # specify a related_name for the groups field to avoid clash with default User model
    groups = models.ManyToManyField(Group, related_name='custom_users', blank=True)
    # specify a related_name for the user_permissions field to avoid clash with default User model
    user_permissions = models.ManyToManyField(Permission, related_name='custom_users', blank=True)

    def get_full_name(self):
        return f"{self.first_name} {self.last_name}" or self.email

    def get_short_name(self):
        return self.email

    def __str__(self):
        return self.email


class UrgentContact(models.Model):
    name = models.CharField(max_length=255)
    email = models.EmailField(help_text="Enter a valid email address.")
    user = models.ForeignKey(CustomUser, on_delete=models.CASCADE, related_name='urgent_contacts')

    def __str__(self):
        return self.name

    def get_email(self):
        return self.email

    @staticmethod
    def get_urgent_contact_emails(user):
        # User' Urgent Contacts' email addresses
        return UrgentContact.objects.filter(user=user).values_list('email', flat=True)

    class Meta:
        verbose_name_plural = "Urgent contacts"
