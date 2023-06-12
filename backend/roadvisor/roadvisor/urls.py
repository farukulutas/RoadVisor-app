"""roadvisor URL Configuration

The `urlpatterns` list routes URLs to views. For more information please see:
    https://docs.djangoproject.com/en/4.1/topics/http/urls/
Examples:
Function views
    1. Add an import:  from my_app import views
    2. Add a URL to urlpatterns:  path('', views.home, name='home')
Class-based views
    1. Add an import:  from other_app.views import Home
    2. Add a URL to urlpatterns:  path('', Home.as_view(), name='home')
Including another URLconf
    1. Import the include() function: from django.urls import include, path
    2. Add a URL to urlpatterns:  path('blog/', include('blog.urls'))
"""
from django.contrib import admin
from django.urls import path
from rest_framework.routers import DefaultRouter

from roadvisorapp.views import (
    activate_account,
    get_csrf_token,
    process_image,
    register,
    send_email,
    login_view,
    logout_view,
    UrgentContactView,
    UserUpdateAPIView,
    PasswordReset,
    ResetPasswordAPI
)

app_name = 'roadvisorapp'
router = DefaultRouter()

urlpatterns = [
    path('api/v1/accounts/register/', register, name='register'),
    path('api/v1/accounts/update/', UserUpdateAPIView.as_view(), name='update'),
    path('api/v1/activate/<int:user_id>/<uuid:token>/', activate_account, name='activate-account'),
    path('api/v1/admin/', admin.site.urls),
    path('api/v1/get_csrf_token/', get_csrf_token, name='get_csrf_token'),
    path('api/v1/login/', login_view, name='login'),
    path('api/v1/logout/', logout_view, name='logout'),
    path('api/v1/password-reset/request/', PasswordReset.as_view(), name='password-reset-request'),
    path('api/v1/password-reset/confirm/<str:encoded_pk>/<str:token>/', ResetPasswordAPI.as_view(), name='password-reset-confirm'),
    path('api/v1/process-image/', process_image, name='process-image'),
    path('api/v1/urgent-contacts/', UrgentContactView.as_view({'get': 'list', 'post': 'create'}), name='urgent-contacts-list'),
    path('api/v1/urgent-contacts/send-email/', send_email, name='send-email'),
    path('api/v1/urgent-contacts/<int:pk>/', UrgentContactView.as_view({'get': 'retrieve', 'put': 'update', 'patch': 'partial_update', 'delete': 'destroy'}), name='urgent-contacts-detail'),
]
