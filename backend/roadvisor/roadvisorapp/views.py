from django.conf import settings
from django.contrib.auth import login, logout
from django.contrib.auth.tokens import PasswordResetTokenGenerator
from django.core.files.storage import default_storage
from django.core.mail import send_mail
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt, ensure_csrf_cookie
from django.urls import reverse
from django.utils.encoding import force_bytes
from django.utils.http import urlsafe_base64_encode
from rest_framework import permissions, generics, status, response, viewsets
from rest_framework.decorators import action, api_view, permission_classes
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView
import os
import subprocess
import uuid

from .models import CustomUser, UrgentContact
from . import serializers
from .serializers import UserSerializer, UrgentContactSerializer


@api_view(['POST'])
@permission_classes([permissions.AllowAny])
def register(request):
    serializer = UserSerializer(data=request.data)
    if serializer.is_valid():
        email = serializer.validated_data.get('email')
        if CustomUser.objects.filter(email=email).exists():
            return Response({'error': 'User with this email already exists.'}, status=status.HTTP_400_BAD_REQUEST)

        user = serializer.save()

        # Generate and save token
        token = uuid.uuid4()
        user.token = token
        user.save()

        # Send confirmation email
        subject = 'Account Activation'
        message = f'Hi {user.first_name},\n\nPlease click the link below to activate your account:\n\n{settings.BASE_URL}/api/v1/activate/{user.id}/{token}/\n\nThank you,\n\nRoadvisor Support Team\n\nNote: Do not reply to this email as it is an automated email.'
        from_email = settings.EMAIL_HOST_USER
        recipient_list = [user.email]
        send_mail(subject, message, from_email, recipient_list)

        return Response({'message': 'Please check your email to activate your account.'}, status=status.HTTP_201_CREATED)
    return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)


@api_view(['GET'])
@permission_classes([permissions.AllowAny])
def activate_account(request, user_id, token):
    try:
        user = CustomUser.objects.get(id=user_id, token=token)
    except CustomUser.DoesNotExist:
        return Response({'error': 'Invalid token'}, status=status.HTTP_400_BAD_REQUEST)

    # Expire token after activation
    user.is_active = True
    user.save()

    return Response({'success': 'Account activated successfully'}, status=status.HTTP_200_OK)


@api_view(['POST'])
@permission_classes([permissions.AllowAny])
def login_view(request):
    serializer = serializers.LoginSerializer(data=request.data)
    if serializer.is_valid():
        user = serializer.validated_data['user']
        login(request, user)
        return Response(serializers.UserSerializer(user).data)
    return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def logout_view(request):
    logout(request)
    return Response({"message": "Logged out successfully."}, status=status.HTTP_200_OK)


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def send_email(request):
    user = request.user
    location = request.data.get('location')

    if not location:
        return Response({'error': 'Location is required.'}, status=status.HTTP_400_BAD_REQUEST)

    recipients = UrgentContact.get_urgent_contact_emails(user)
    if not recipients:
        return Response({'error': 'Urgent Contact not found. Please add Urgent Contact to use this feature.'}, status=status.HTTP_400_BAD_REQUEST)

    subject = 'Urgent: Car accident - Requesting immediate assistance'
    message = f"Emergency! Please contact {user.first_name} {user.last_name} immediately. They have been in a car accident and need help. Their location is: https://www.google.com/maps/?q={location}."
    from_email = settings.EMAIL_HOST_USER
    recipient_list = recipients

    try:
        send_mail(subject, message, from_email, recipient_list)
    except Exception as e:
        return Response({'error': str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)

    return Response({'message': 'Urgent contacts have been informed.'}, status=status.HTTP_200_OK)

@csrf_exempt
def process_image(request):
    if request.method == 'POST':
        image = request.FILES.get('image', None)
        if image is None:
            return JsonResponse({"error": "No image file found in the request"}, status=400)

        image_ext = os.path.splitext(image.name)[1]
        if image_ext.lower() not in ['.jpg', '.jpeg']:
            return JsonResponse({"error": "Invalid file format"}, status=400)

        unique_filename = str(uuid.uuid4()) + image_ext
        default_storage.save(os.path.join('uploaded_images', unique_filename), image)

        command = f"python3 ../yolov7/detect.py --weights ../yolov7/epoch_098.pt --conf 0.5 --img-size 640 --source ../uploaded_images/{unique_filename} --no-trace"
        output = subprocess.check_output(command, shell=True)
        result = output.decode("utf-8")

        # Remove the saved file after processing
        file_path = os.path.join('uploaded_images', unique_filename)
        if default_storage.exists(file_path):
            default_storage.delete(file_path)

        return JsonResponse({"message": f"Image processed with filename: {unique_filename}, result: {result}"}, status=200)

    return JsonResponse({"error": "Invalid request method"}, status=405)

@ensure_csrf_cookie
def get_csrf_token(request):
    return JsonResponse({'detail': 'CSRF cookie set'})

class UserUpdateAPIView(APIView):
    permission_classes = (permissions.IsAuthenticated,)

    def patch(self, request, *args, **kwargs):
        data = request.data
        partial = True
        user = request.user

        if 'email' in data or 'first_name' in data or 'last_name' in data or 'password' in data or 'old_password' in data:
            serializer = serializers.UserUpdateSerializer(user, data=data, partial=partial)
            if serializer.is_valid():
                serializer.save()
                return Response({'success': True})
            else:
                return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
        else:
            return Response({'error': 'At least one of the following fields must be provided: email, first_name, last_name, password, old_password.'}, status=status.HTTP_400_BAD_REQUEST)

class PasswordReset(generics.GenericAPIView):
    """
    Request for Password Reset Link.
    """

    serializer_class = serializers.EmailSerializer

    def post(self, request):
        """
        Create token.
        """
        serializer = self.serializer_class(data=request.data)
        serializer.is_valid(raise_exception=True)
        email = serializer.data["email"]
        try:
            user = CustomUser.objects.get(email=email)
        except CustomUser.DoesNotExist:
            return Response(
                {"message": "If the email you entered is valid, you will receive instructions on how to reset your password in your email shortly."},
                status=status.HTTP_200_OK,
            )
        encoded_pk = urlsafe_base64_encode(force_bytes(user.pk))
        token = PasswordResetTokenGenerator().make_token(user)
        reset_url = reverse(
            "password-reset-confirm",
            kwargs={"encoded_pk": encoded_pk, "token": token},
        )
        reset_link = f"{settings.BASE_URL}{reset_url}"

        # send the reset_link as mail to the user.
        subject = 'Password Reset Request'
        message = f'Hello {user.first_name},\n\nWe received a request to reset your password for your account with us. To complete the process, please click on the link below.\n\n{reset_link}\n\nThank you,\n\nRoadvisor Support Team\n\nNote: Do not reply to this email as it is an automated email.'
        from_email = settings.EMAIL_HOST_USER
        recipient_list = [user.email]
        send_mail(subject, message, from_email, recipient_list)

        return Response(
            {"message": "If the email you entered is valid, you will receive instructions on how to reset your password in your email shortly."},
            status=status.HTTP_200_OK,
        )


class ResetPasswordAPI(generics.GenericAPIView):
    """
    Verify and Reset Password Token View.
    """

    serializer_class = serializers.ResetPasswordSerializer

    def patch(self, request, *args, **kwargs):
        """
        Verify token & encoded_pk and then reset the password.
        """
        serializer = self.serializer_class(
            data=request.data, context={"kwargs": kwargs}
        )
        serializer.is_valid(raise_exception=True)
        return response.Response(
            {"message": "Password reset complete"},
            status=status.HTTP_200_OK,
        )


class UrgentContactView(viewsets.ModelViewSet):
    queryset = UrgentContact.objects.all()
    serializer_class = UrgentContactSerializer
    permission_classes = [IsAuthenticated]

    def list(self, request, *args, **kwargs):
        queryset = self.get_queryset()
        serializer = self.get_serializer(queryset, many=True)
        return Response(serializer.data)

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        self.perform_create(serializer)
        headers = self.get_success_headers(serializer.data)
        return Response(serializer.data, status=status.HTTP_201_CREATED, headers=headers)

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)

    def retrieve(self, request, *args, **kwargs):
        instance = self.get_object()
        serializer = self.get_serializer(instance)
        return Response(serializer.data)

    def update(self, request, *args, **kwargs):
        instance = self.get_object()
        serializer = self.get_serializer(instance, data=request.data)
        serializer.is_valid(raise_exception=True)
        self.perform_update(serializer)
        return Response(serializer.data)

    def perform_update(self, serializer):
        serializer.save(user=self.request.user)

    def destroy(self, request, *args, **kwargs):
        instance = self.get_object()
        self.perform_destroy(instance)
        return Response(status=status.HTTP_204_NO_CONTENT)

    def perform_destroy(self, instance):
        instance.delete()

    @action(detail=False, methods=['get'])
    def my_urgent_contacts(self, request):
        queryset = UrgentContact.objects.filter(user=self.request.user)
        serializer = UrgentContactSerializer(queryset, many=True)
        return Response(serializer.data)

    @action(detail=True, methods=['post'])
    def add_urgent_contact(self, request, pk=None):
        instance = self.get_object()
        serializer = UrgentContactSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        serializer.save(user=self.request.user)
        return Response(serializer.data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=['patch'])
    def partial_update(self, request, *args, **kwargs):
        instance = self.get_object()
        serializer = self.get_serializer(instance, data=request.data, partial=True)
        serializer.is_valid(raise_exception=True)
        serializer.save()
        return Response(serializer.data)

    @action(detail=True, methods=['delete'])
    def delete_urgent_contact(self, request, pk=None):
        instance = self.get_object()
        try:
            contact = instance.urgent_contacts.get(id=request.data['id'])
            contact.delete()
            return Response(status=status.HTTP_204_NO_CONTENT)
        except CustomUser.ObjectDoesNotExist:
            return Response(status=status.HTTP_404_NOT_FOUND)
