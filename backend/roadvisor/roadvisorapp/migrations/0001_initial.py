# Generated by Django 4.1.7 on 2023-02-26 17:08

from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion
import uuid


class Migration(migrations.Migration):

    initial = True

    dependencies = [
        ('auth', '0012_alter_user_first_name_max_length'),
    ]

    operations = [
        migrations.CreateModel(
            name='CustomUser',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('password', models.CharField(max_length=128, verbose_name='password')),
                ('last_login', models.DateTimeField(blank=True, null=True, verbose_name='last login')),
                ('is_superuser', models.BooleanField(default=False, help_text='Designates that this user has all permissions without explicitly assigning them.', verbose_name='superuser status')),
                ('email', models.EmailField(help_text='Enter a valid email address.', max_length=254, unique=True)),
                ('first_name', models.CharField(help_text='Enter your first name.', max_length=30)),
                ('last_name', models.CharField(help_text='Enter your last name.', max_length=30)),
                ('is_active', models.BooleanField(default=False, help_text='Designates whether this user should be treated as active. Unselect this instead of deleting accounts.')),
                ('is_staff', models.BooleanField(default=False, help_text='Designates whether the user can log into this admin site.')),
                ('date_joined', models.DateTimeField(auto_now_add=True)),
                ('token', models.UUIDField(default=uuid.uuid4, editable=False, help_text='Unique identifier for the user.', unique=True)),
                ('groups', models.ManyToManyField(blank=True, related_name='custom_users', to='auth.group')),
                ('user_permissions', models.ManyToManyField(blank=True, related_name='custom_users', to='auth.permission')),
            ],
            options={
                'db_table': 'custom_user',
            },
        ),
        migrations.CreateModel(
            name='UrgentContact',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('name', models.CharField(max_length=255)),
                ('phone_number', models.CharField(max_length=20)),
                ('user', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='urgent_contacts', to=settings.AUTH_USER_MODEL)),
            ],
        ),
    ]