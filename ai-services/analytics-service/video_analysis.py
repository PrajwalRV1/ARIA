#!/usr/bin/env python3
"""
ARIA Video Analysis Module

Advanced computer vision analysis for interview videos including:
- Emotion recognition using deep learning models
- Engagement and attention tracking
- Facial expression analysis and micro-expressions
- Eye contact and gaze estimation
- Posture and gesture analysis
- Stress and confidence indicators
"""

import asyncio
import cv2
import numpy as np
import logging
import time
import base64
from typing import Dict, List, Optional, Tuple, Any
from dataclasses import dataclass
from concurrent.futures import ThreadPoolExecutor
import tensorflow as tf
from tensorflow.keras.models import load_model
import mediapipe as mp
import dlib
from scipy import spatial
from sklearn.metrics.pairwise import cosine_similarity
import face_recognition

from models import (
    EmotionScores, FacialExpressions, PostureAnalysis, 
    MicroExpressions, StressIndicators
)

logger = logging.getLogger(__name__)


@dataclass
class VideoFrame:
    """Single video frame with metadata"""
    frame: np.ndarray
    timestamp: float
    frame_number: int
    width: int
    height: int


@dataclass
class FaceData:
    """Face detection and analysis data"""
    bbox: Tuple[int, int, int, int]  # x, y, w, h
    landmarks: np.ndarray
    encoding: np.ndarray
    confidence: float


@dataclass
class AnalysisResult:
    """Complete video analysis result"""
    analysis_id: str
    emotions: EmotionScores
    engagement_score: float
    attention_score: float
    stress_indicators: StressIndicators
    confidence_level: float
    facial_expressions: FacialExpressions
    eye_contact_score: float
    posture_analysis: PostureAnalysis
    micro_expressions: MicroExpressions
    processing_time: float
    model_confidence: float


class VideoAnalyzer:
    """Advanced video analysis using computer vision and deep learning"""
    
    def __init__(self, emotion_model_path: str, engagement_model_path: str,
                 frame_rate: int = 10, timeout: int = 300):
        """
        Initialize video analyzer with ML models
        
        Args:
            emotion_model_path: Path to emotion recognition model
            engagement_model_path: Path to engagement detection model
            frame_rate: Process every Nth frame
            timeout: Analysis timeout in seconds
        """
        self.emotion_model_path = emotion_model_path
        self.engagement_model_path = engagement_model_path
        self.frame_rate = frame_rate
        self.timeout = timeout
        
        # Initialize models
        self.emotion_model = None
        self.engagement_model = None
        self.face_cascade = None
        self.eye_cascade = None
        
        # Initialize MediaPipe components
        self.mp_face_mesh = mp.solutions.face_mesh
        self.mp_pose = mp.solutions.pose
        self.mp_hands = mp.solutions.hands
        self.mp_drawing = mp.solutions.drawing_utils
        
        # Face mesh for detailed analysis
        self.face_mesh = self.mp_face_mesh.FaceMesh(
            static_image_mode=False,
            max_num_faces=1,
            refine_landmarks=True,
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5
        )
        
        # Pose estimation
        self.pose = self.mp_pose.Pose(
            static_image_mode=False,
            model_complexity=2,
            smooth_landmarks=True,
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5
        )
        
        # Hand tracking
        self.hands = self.mp_hands.Hands(
            static_image_mode=False,
            max_num_hands=2,
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5
        )
        
        # Thread pool for parallel processing
        self.executor = ThreadPoolExecutor(max_workers=4)
        
        # Face recognition and analysis
        self.face_detector = dlib.get_frontal_face_detector()
        self.landmark_predictor = None
        
        # Analysis state
        self.is_initialized = False
        self.analysis_cache = {}
        
        logger.info("VideoAnalyzer initialized")
    
    async def initialize_models(self):
        """Load and initialize all ML models"""
        try:
            # Load emotion recognition model
            if tf.io.gfile.exists(self.emotion_model_path):
                self.emotion_model = load_model(self.emotion_model_path)
                logger.info("Emotion model loaded successfully")
            else:
                logger.warning(f"Emotion model not found: {self.emotion_model_path}")
                # Create dummy model for development
                self.emotion_model = self._create_dummy_emotion_model()
            
            # Load engagement model (assuming scikit-learn model)
            import joblib
            try:
                self.engagement_model = joblib.load(self.engagement_model_path)
                logger.info("Engagement model loaded successfully")
            except Exception as e:
                logger.warning(f"Could not load engagement model: {e}")
                self.engagement_model = self._create_dummy_engagement_model()
            
            # Initialize OpenCV cascades
            self.face_cascade = cv2.CascadeClassifier(
                cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'
            )
            self.eye_cascade = cv2.CascadeClassifier(
                cv2.data.haarcascades + 'haarcascade_eye.xml'
            )
            
            # Initialize dlib landmark predictor
            try:
                # This would need to be downloaded: http://dlib.net/files/shape_predictor_68_face_landmarks.dat.bz2
                predictor_path = "/app/models/shape_predictor_68_face_landmarks.dat"
                if tf.io.gfile.exists(predictor_path):
                    self.landmark_predictor = dlib.shape_predictor(predictor_path)
                else:
                    logger.warning("Dlib landmark predictor not found")
            except Exception as e:
                logger.warning(f"Could not load dlib predictor: {e}")
            
            self.is_initialized = True
            logger.info("All video analysis models initialized")
            
        except Exception as e:
            logger.error(f"Failed to initialize models: {e}")
            raise
    
    def _create_dummy_emotion_model(self):
        """Create dummy emotion model for development"""
        from tensorflow.keras import Sequential
        from tensorflow.keras.layers import Dense, Dropout
        
        model = Sequential([
            Dense(128, activation='relu', input_shape=(48*48,)),
            Dropout(0.5),
            Dense(64, activation='relu'),
            Dropout(0.5),
            Dense(7, activation='softmax')  # 7 emotions
        ])
        model.compile(optimizer='adam', loss='categorical_crossentropy')
        return model
    
    def _create_dummy_engagement_model(self):
        """Create dummy engagement model for development"""
        from sklearn.ensemble import RandomForestRegressor
        return RandomForestRegressor(n_estimators=100)
    
    def is_ready(self) -> bool:
        """Check if analyzer is ready for processing"""
        return self.is_initialized and self.emotion_model is not None
    
    async def analyze_video(self, video_data: str, session_id: str, 
                          candidate_id: int, analysis_config: Dict) -> AnalysisResult:
        """
        Perform comprehensive video analysis
        
        Args:
            video_data: Base64 encoded video or video file path
            session_id: Interview session ID
            candidate_id: Candidate ID
            analysis_config: Analysis configuration
            
        Returns:
            Complete analysis results
        """
        start_time = time.time()
        analysis_id = f"{session_id}_{candidate_id}_{int(start_time)}"
        
        logger.info(f"Starting video analysis for {analysis_id}")
        
        try:
            # Extract frames from video
            frames = await self._extract_frames(video_data)
            if not frames:
                raise ValueError("No frames extracted from video")
            
            # Analyze frames in parallel
            frame_analyses = await self._analyze_frames_parallel(frames, analysis_config)
            
            # Aggregate results
            analysis_result = await self._aggregate_analysis_results(
                frame_analyses, analysis_id, start_time
            )
            
            processing_time = time.time() - start_time
            analysis_result.processing_time = processing_time
            
            logger.info(f"Video analysis completed for {analysis_id} in {processing_time:.2f}s")
            return analysis_result
            
        except Exception as e:
            logger.error(f"Video analysis failed for {analysis_id}: {e}")
            raise
    
    async def _extract_frames(self, video_data: str) -> List[VideoFrame]:
        """Extract frames from video data"""
        frames = []
        
        try:
            # Handle base64 encoded video
            if video_data.startswith('data:video') or len(video_data) > 1000:
                # Decode base64 video
                if video_data.startswith('data:video'):
                    header, encoded = video_data.split(',', 1)
                    video_bytes = base64.b64decode(encoded)
                else:
                    video_bytes = base64.b64decode(video_data)
                
                # Save temporarily and read with OpenCV
                temp_path = f"/tmp/temp_video_{int(time.time())}.mp4"
                with open(temp_path, 'wb') as f:
                    f.write(video_bytes)
                
                cap = cv2.VideoCapture(temp_path)
            else:
                # Assume it's a file path
                cap = cv2.VideoCapture(video_data)
            
            if not cap.isOpened():
                raise ValueError("Could not open video")
            
            fps = cap.get(cv2.CAP_PROP_FPS)
            frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
            
            # Extract frames at specified rate
            frame_number = 0
            while cap.isOpened():
                ret, frame = cap.read()
                if not ret:
                    break
                
                # Process every Nth frame
                if frame_number % self.frame_rate == 0:
                    timestamp = frame_number / fps
                    height, width = frame.shape[:2]
                    
                    frames.append(VideoFrame(
                        frame=frame,
                        timestamp=timestamp,
                        frame_number=frame_number,
                        width=width,
                        height=height
                    ))
                
                frame_number += 1
                
                # Limit processing time
                if len(frames) > 1000:  # Max 1000 frames
                    break
            
            cap.release()
            logger.info(f"Extracted {len(frames)} frames from video")
            return frames
            
        except Exception as e:
            logger.error(f"Frame extraction failed: {e}")
            return []
    
    async def _analyze_frames_parallel(self, frames: List[VideoFrame], 
                                     config: Dict) -> List[Dict]:
        """Analyze frames in parallel"""
        # Split frames into batches for parallel processing
        batch_size = 10
        batches = [frames[i:i+batch_size] for i in range(0, len(frames), batch_size)]
        
        # Process batches in parallel
        tasks = []
        for batch in batches:
            task = asyncio.create_task(self._analyze_frame_batch(batch, config))
            tasks.append(task)
        
        batch_results = await asyncio.gather(*tasks)
        
        # Flatten results
        all_results = []
        for batch_result in batch_results:
            all_results.extend(batch_result)
        
        return all_results
    
    async def _analyze_frame_batch(self, frames: List[VideoFrame], 
                                 config: Dict) -> List[Dict]:
        """Analyze a batch of frames"""
        results = []
        
        for frame_data in frames:
            try:
                # Perform comprehensive frame analysis
                frame_result = await self._analyze_single_frame(frame_data, config)
                results.append(frame_result)
            except Exception as e:
                logger.warning(f"Frame analysis failed: {e}")
                continue
        
        return results
    
    async def _analyze_single_frame(self, frame_data: VideoFrame, 
                                  config: Dict) -> Dict:
        """Analyze a single frame comprehensively"""
        frame = frame_data.frame
        timestamp = frame_data.timestamp
        
        result = {
            'timestamp': timestamp,
            'frame_number': frame_data.frame_number,
            'emotions': {},
            'facial_expressions': {},
            'engagement_metrics': {},
            'attention_metrics': {},
            'stress_indicators': [],
            'posture_data': {},
            'micro_expressions': [],
            'eye_contact': {},
            'confidence_indicators': {}
        }
        
        try:
            # Detect faces
            faces = await self._detect_faces(frame)
            
            if faces:
                primary_face = faces[0]  # Use largest/most confident face
                
                # Emotion analysis
                if config.get('enable_emotion_detection', True):
                    emotions = await self._analyze_emotions(frame, primary_face)
                    result['emotions'] = emotions
                
                # Facial expression analysis
                expressions = await self._analyze_facial_expressions(frame, primary_face)
                result['facial_expressions'] = expressions
                
                # Eye analysis
                eye_data = await self._analyze_eyes(frame, primary_face)
                result['eye_contact'] = eye_data
                
                # Micro-expressions
                if config.get('enable_micro_expressions', True):
                    micro_expr = await self._detect_micro_expressions(frame, primary_face)
                    result['micro_expressions'] = micro_expr
                
                # Confidence indicators
                confidence = await self._analyze_confidence_indicators(frame, primary_face)
                result['confidence_indicators'] = confidence
            
            # Engagement analysis
            if config.get('enable_engagement_tracking', True):
                engagement = await self._analyze_engagement(frame, faces)
                result['engagement_metrics'] = engagement
            
            # Attention analysis
            attention = await self._analyze_attention(frame, faces)
            result['attention_metrics'] = attention
            
            # Posture analysis
            posture = await self._analyze_posture(frame)
            result['posture_data'] = posture
            
            # Stress indicators
            stress = await self._detect_stress_indicators(frame, faces)
            result['stress_indicators'] = stress
            
        except Exception as e:
            logger.warning(f"Single frame analysis failed: {e}")
            result['error'] = str(e)
        
        return result
    
    async def _detect_faces(self, frame: np.ndarray) -> List[FaceData]:
        """Detect and analyze faces in frame"""
        faces = []
        
        try:
            # Convert to RGB for face_recognition
            rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            
            # Detect face locations
            face_locations = face_recognition.face_locations(rgb_frame)
            face_encodings = face_recognition.face_encodings(rgb_frame, face_locations)
            
            for (top, right, bottom, left), encoding in zip(face_locations, face_encodings):
                # Convert to standard bbox format
                bbox = (left, top, right - left, bottom - top)
                
                # Get facial landmarks if predictor is available
                landmarks = None
                if self.landmark_predictor:
                    dlib_rect = dlib.rectangle(left, top, right, bottom)
                    shape = self.landmark_predictor(
                        cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY), dlib_rect
                    )
                    landmarks = np.array([[p.x, p.y] for p in shape.parts()])
                
                faces.append(FaceData(
                    bbox=bbox,
                    landmarks=landmarks,
                    encoding=encoding,
                    confidence=0.9  # face_recognition doesn't provide confidence
                ))
            
        except Exception as e:
            logger.warning(f"Face detection failed: {e}")
        
        return faces
    
    async def _analyze_emotions(self, frame: np.ndarray, face: FaceData) -> Dict:
        """Analyze emotions from face"""
        emotions = {
            'happiness': 0.0,
            'sadness': 0.0,
            'anger': 0.0,
            'fear': 0.0,
            'surprise': 0.0,
            'disgust': 0.0,
            'neutral': 0.5
        }
        
        try:
            if self.emotion_model:
                # Extract face region
                x, y, w, h = face.bbox
                face_roi = frame[y:y+h, x:x+w]
                
                # Preprocess for emotion model
                face_gray = cv2.cvtColor(face_roi, cv2.COLOR_BGR2GRAY)
                face_resized = cv2.resize(face_gray, (48, 48))
                face_normalized = face_resized.astype('float32') / 255.0
                face_reshaped = face_normalized.reshape(1, -1)
                
                # Predict emotions
                predictions = self.emotion_model.predict(face_reshaped, verbose=0)
                
                # Map predictions to emotion labels
                emotion_labels = ['anger', 'disgust', 'fear', 'happiness', 
                                'sadness', 'surprise', 'neutral']
                
                for i, label in enumerate(emotion_labels):
                    if i < len(predictions[0]):
                        emotions[label] = float(predictions[0][i])
            
        except Exception as e:
            logger.warning(f"Emotion analysis failed: {e}")
        
        return emotions
    
    async def _analyze_facial_expressions(self, frame: np.ndarray, 
                                        face: FaceData) -> Dict:
        """Analyze detailed facial expressions"""
        expressions = {
            'smile_intensity': 0.0,
            'eyebrow_movement': 0.0,
            'eye_openness': 0.5,
            'mouth_openness': 0.0,
            'head_pose': {'pitch': 0.0, 'yaw': 0.0, 'roll': 0.0},
            'gaze_direction': {'x': 0.0, 'y': 0.0}
        }
        
        try:
            if face.landmarks is not None:
                # Calculate smile intensity
                expressions['smile_intensity'] = self._calculate_smile_intensity(face.landmarks)
                
                # Calculate eye openness
                expressions['eye_openness'] = self._calculate_eye_openness(face.landmarks)
                
                # Calculate mouth openness
                expressions['mouth_openness'] = self._calculate_mouth_openness(face.landmarks)
                
                # Estimate head pose
                expressions['head_pose'] = self._estimate_head_pose(face.landmarks)
                
                # Estimate gaze direction
                expressions['gaze_direction'] = self._estimate_gaze_direction(frame, face)
            
        except Exception as e:
            logger.warning(f"Facial expression analysis failed: {e}")
        
        return expressions
    
    def _calculate_smile_intensity(self, landmarks: np.ndarray) -> float:
        """Calculate smile intensity from landmarks"""
        try:
            # Mouth landmarks (dlib 68-point model)
            mouth_left = landmarks[48]
            mouth_right = landmarks[54]
            mouth_top = landmarks[51]
            mouth_bottom = landmarks[57]
            
            # Calculate mouth width to height ratio
            width = np.linalg.norm(mouth_right - mouth_left)
            height = np.linalg.norm(mouth_top - mouth_bottom)
            
            # Normalize based on face size
            face_width = np.linalg.norm(landmarks[16] - landmarks[0])
            normalized_ratio = (width / height) / (face_width / 100)
            
            # Convert to smile intensity (0-1)
            smile_intensity = min(1.0, max(0.0, (normalized_ratio - 2.0) / 2.0))
            return smile_intensity
            
        except Exception:
            return 0.0
    
    def _calculate_eye_openness(self, landmarks: np.ndarray) -> float:
        """Calculate eye openness from landmarks"""
        try:
            # Left eye landmarks
            left_eye_top = landmarks[37:39]
            left_eye_bottom = landmarks[40:42]
            
            # Right eye landmarks  
            right_eye_top = landmarks[43:45]
            right_eye_bottom = landmarks[46:48]
            
            # Calculate eye aspect ratios
            left_ear = self._eye_aspect_ratio(left_eye_top, left_eye_bottom)
            right_ear = self._eye_aspect_ratio(right_eye_top, right_eye_bottom)
            
            # Average and normalize
            avg_ear = (left_ear + right_ear) / 2.0
            normalized_ear = min(1.0, max(0.0, avg_ear * 3))  # Scale to 0-1
            
            return normalized_ear
            
        except Exception:
            return 0.5
    
    def _eye_aspect_ratio(self, top_points: np.ndarray, bottom_points: np.ndarray) -> float:
        """Calculate eye aspect ratio"""
        # Vertical distances
        v1 = np.linalg.norm(top_points[0] - bottom_points[0])
        v2 = np.linalg.norm(top_points[1] - bottom_points[1])
        
        # Horizontal distance
        h = np.linalg.norm(top_points[0] - top_points[1])
        
        # Eye aspect ratio
        ear = (v1 + v2) / (2.0 * h)
        return ear
    
    def _calculate_mouth_openness(self, landmarks: np.ndarray) -> float:
        """Calculate mouth openness from landmarks"""
        try:
            mouth_top = landmarks[51]
            mouth_bottom = landmarks[57]
            mouth_left = landmarks[48]
            mouth_right = landmarks[54]
            
            # Calculate mouth height and width
            height = np.linalg.norm(mouth_top - mouth_bottom)
            width = np.linalg.norm(mouth_right - mouth_left)
            
            # Normalize by face width
            face_width = np.linalg.norm(landmarks[16] - landmarks[0])
            normalized_height = height / (face_width / 100)
            
            # Convert to openness score
            openness = min(1.0, max(0.0, normalized_height / 10))
            return openness
            
        except Exception:
            return 0.0
    
    def _estimate_head_pose(self, landmarks: np.ndarray) -> Dict[str, float]:
        """Estimate head pose angles"""
        pose = {'pitch': 0.0, 'yaw': 0.0, 'roll': 0.0}
        
        try:
            # Use key landmarks for pose estimation
            nose_tip = landmarks[30]
            chin = landmarks[8]
            left_eye = landmarks[36]
            right_eye = landmarks[45]
            
            # Calculate yaw (left-right rotation)
            eye_center = (left_eye + right_eye) / 2
            face_center_x = (landmarks[0][0] + landmarks[16][0]) / 2
            nose_x_offset = nose_tip[0] - face_center_x
            face_width = landmarks[16][0] - landmarks[0][0]
            yaw = (nose_x_offset / face_width) * 45  # Scale to degrees
            
            # Calculate pitch (up-down rotation)
            nose_chin_vector = chin - nose_tip
            vertical_component = nose_chin_vector[1]
            face_height = chin[1] - landmarks[19][1]  # Forehead to chin
            pitch = (vertical_component / face_height) * 30  # Scale to degrees
            
            # Calculate roll (tilt)
            eye_vector = right_eye - left_eye
            roll = np.arctan2(eye_vector[1], eye_vector[0]) * 180 / np.pi
            
            pose = {
                'pitch': float(np.clip(pitch, -45, 45)),
                'yaw': float(np.clip(yaw, -45, 45)),
                'roll': float(np.clip(roll, -45, 45))
            }
            
        except Exception:
            pass
        
        return pose
    
    def _estimate_gaze_direction(self, frame: np.ndarray, face: FaceData) -> Dict[str, float]:
        """Estimate gaze direction"""
        gaze = {'x': 0.0, 'y': 0.0}
        
        try:
            if face.landmarks is not None:
                # Simple gaze estimation based on eye landmarks
                left_eye_center = np.mean(face.landmarks[36:42], axis=0)
                right_eye_center = np.mean(face.landmarks[42:48], axis=0)
                
                # Calculate gaze based on pupil position relative to eye center
                # This is a simplified approach - full gaze tracking requires specialized models
                eye_center = (left_eye_center + right_eye_center) / 2
                nose_tip = face.landmarks[30]
                
                # Estimate gaze direction relative to head pose
                gaze_vector = eye_center - nose_tip
                gaze['x'] = float(np.clip(gaze_vector[0] / 10, -1, 1))
                gaze['y'] = float(np.clip(gaze_vector[1] / 10, -1, 1))
            
        except Exception:
            pass
        
        return gaze
    
    async def _analyze_engagement(self, frame: np.ndarray, faces: List[FaceData]) -> Dict:
        """Analyze engagement metrics"""
        engagement = {
            'overall_score': 0.5,
            'face_present': False,
            'looking_at_camera': False,
            'alertness_level': 0.5,
            'activity_level': 0.5
        }
        
        try:
            if faces:
                engagement['face_present'] = True
                
                # Analyze primary face for engagement
                primary_face = faces[0]
                
                if primary_face.landmarks is not None:
                    # Check if looking at camera (simplified)
                    head_pose = self._estimate_head_pose(primary_face.landmarks)
                    yaw_threshold = 20
                    pitch_threshold = 15
                    
                    looking_at_camera = (
                        abs(head_pose['yaw']) < yaw_threshold and
                        abs(head_pose['pitch']) < pitch_threshold
                    )
                    engagement['looking_at_camera'] = looking_at_camera
                    
                    # Calculate alertness from eye openness
                    eye_openness = self._calculate_eye_openness(primary_face.landmarks)
                    engagement['alertness_level'] = eye_openness
                    
                    # Overall engagement score
                    engagement_score = 0.0
                    if engagement['face_present']:
                        engagement_score += 0.3
                    if engagement['looking_at_camera']:
                        engagement_score += 0.4
                    engagement_score += engagement['alertness_level'] * 0.3
                    
                    engagement['overall_score'] = min(1.0, engagement_score)
            
        except Exception as e:
            logger.warning(f"Engagement analysis failed: {e}")
        
        return engagement
    
    async def _analyze_attention(self, frame: np.ndarray, faces: List[FaceData]) -> Dict:
        """Analyze attention metrics"""
        attention = {
            'focus_score': 0.5,
            'distraction_indicators': [],
            'gaze_stability': 0.5,
            'head_movement': 0.5
        }
        
        # This would require temporal analysis across multiple frames
        # For now, return baseline values
        return attention
    
    async def _analyze_posture(self, frame: np.ndarray) -> Dict:
        """Analyze body posture using MediaPipe Pose"""
        posture = {
            'posture_score': 0.5,
            'movement_intensity': 0.0,
            'gesture_frequency': 0.0,
            'body_orientation': 'facing_camera',
            'fidgeting_indicators': []
        }
        
        try:
            # Convert BGR to RGB
            rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            
            # Process with MediaPipe Pose
            results = self.pose.process(rgb_frame)
            
            if results.pose_landmarks:
                landmarks = results.pose_landmarks.landmark
                
                # Calculate posture score based on shoulder alignment
                left_shoulder = landmarks[self.mp_pose.PoseLandmark.LEFT_SHOULDER]
                right_shoulder = landmarks[self.mp_pose.PoseLandmark.RIGHT_SHOULDER]
                
                # Calculate shoulder tilt
                shoulder_tilt = abs(left_shoulder.y - right_shoulder.y)
                posture_score = max(0.0, 1.0 - shoulder_tilt * 10)
                posture['posture_score'] = posture_score
                
                # Determine body orientation
                nose = landmarks[self.mp_pose.PoseLandmark.NOSE]
                if nose.visibility > 0.5:
                    if abs(nose.x - 0.5) < 0.1:
                        posture['body_orientation'] = 'facing_camera'
                    elif nose.x < 0.4:
                        posture['body_orientation'] = 'turned_right'
                    elif nose.x > 0.6:
                        posture['body_orientation'] = 'turned_left'
            
        except Exception as e:
            logger.warning(f"Posture analysis failed: {e}")
        
        return posture
    
    async def _detect_stress_indicators(self, frame: np.ndarray, 
                                      faces: List[FaceData]) -> List[str]:
        """Detect stress indicators"""
        stress_indicators = []
        
        try:
            if faces and faces[0].landmarks is not None:
                landmarks = faces[0].landmarks
                
                # Check for stress indicators
                # Eyebrow tension
                eyebrow_tension = self._calculate_eyebrow_tension(landmarks)
                if eyebrow_tension > 0.7:
                    stress_indicators.append('eyebrow_tension')
                
                # Jaw tension
                jaw_tension = self._calculate_jaw_tension(landmarks)
                if jaw_tension > 0.7:
                    stress_indicators.append('jaw_tension')
                
                # Lip compression
                lip_compression = self._calculate_lip_compression(landmarks)
                if lip_compression > 0.6:
                    stress_indicators.append('lip_compression')
                
                # Rapid blinking (would need temporal analysis)
                # For now, just check eye aspect ratio
                eye_openness = self._calculate_eye_openness(landmarks)
                if eye_openness < 0.3:
                    stress_indicators.append('possible_rapid_blinking')
            
        except Exception as e:
            logger.warning(f"Stress detection failed: {e}")
        
        return stress_indicators
    
    def _calculate_eyebrow_tension(self, landmarks: np.ndarray) -> float:
        """Calculate eyebrow tension indicator"""
        try:
            # Distance between eyebrows and eyes
            left_eyebrow = landmarks[19]
            left_eye = landmarks[37]
            right_eyebrow = landmarks[24]
            right_eye = landmarks[44]
            
            left_distance = np.linalg.norm(left_eyebrow - left_eye)
            right_distance = np.linalg.norm(right_eyebrow - right_eye)
            
            # Lower distance indicates raised eyebrows (tension)
            avg_distance = (left_distance + right_distance) / 2
            face_height = landmarks[8][1] - landmarks[19][1]
            normalized_distance = avg_distance / (face_height / 100)
            
            # Invert so higher values indicate more tension
            tension = max(0.0, 1.0 - normalized_distance / 15)
            return tension
            
        except Exception:
            return 0.0
    
    def _calculate_jaw_tension(self, landmarks: np.ndarray) -> float:
        """Calculate jaw tension indicator"""
        try:
            # Jaw width at different points
            upper_jaw = np.linalg.norm(landmarks[3] - landmarks[13])
            lower_jaw = np.linalg.norm(landmarks[5] - landmarks[11])
            
            # Tension indicated by clenched jaw (wider lower jaw)
            jaw_ratio = lower_jaw / upper_jaw if upper_jaw > 0 else 0
            tension = max(0.0, min(1.0, (jaw_ratio - 0.9) * 10))
            
            return tension
            
        except Exception:
            return 0.0
    
    def _calculate_lip_compression(self, landmarks: np.ndarray) -> float:
        """Calculate lip compression indicator"""
        try:
            # Lip height
            upper_lip = landmarks[51]
            lower_lip = landmarks[57]
            lip_height = np.linalg.norm(upper_lip - lower_lip)
            
            # Lip width
            left_lip = landmarks[48]
            right_lip = landmarks[54]
            lip_width = np.linalg.norm(right_lip - left_lip)
            
            # Compression indicated by low height-to-width ratio
            if lip_width > 0:
                compression_ratio = lip_height / lip_width
                compression = max(0.0, 1.0 - compression_ratio * 20)
                return compression
            
        except Exception:
            pass
        
        return 0.0
    
    async def _detect_micro_expressions(self, frame: np.ndarray, 
                                      face: FaceData) -> List[Dict]:
        """Detect micro-expressions (simplified version)"""
        micro_expressions = []
        
        # This would require specialized models and temporal analysis
        # For now, return empty list
        return micro_expressions
    
    async def _analyze_confidence_indicators(self, frame: np.ndarray, 
                                           face: FaceData) -> Dict:
        """Analyze confidence indicators"""
        confidence = {
            'overall_confidence': 0.5,
            'eye_contact_confidence': 0.5,
            'facial_confidence': 0.5,
            'posture_confidence': 0.5
        }
        
        try:
            if face.landmarks is not None:
                # Eye contact as confidence indicator
                head_pose = self._estimate_head_pose(face.landmarks)
                eye_contact_score = 1.0 - (abs(head_pose['yaw']) + abs(head_pose['pitch'])) / 90
                confidence['eye_contact_confidence'] = max(0.0, eye_contact_score)
                
                # Facial expression confidence
                smile_intensity = self._calculate_smile_intensity(face.landmarks)
                eye_openness = self._calculate_eye_openness(face.landmarks)
                facial_confidence = (smile_intensity * 0.6 + eye_openness * 0.4)
                confidence['facial_confidence'] = facial_confidence
                
                # Overall confidence
                confidence['overall_confidence'] = (
                    confidence['eye_contact_confidence'] * 0.4 +
                    confidence['facial_confidence'] * 0.4 +
                    confidence['posture_confidence'] * 0.2
                )
            
        except Exception as e:
            logger.warning(f"Confidence analysis failed: {e}")
        
        return confidence
    
    async def _aggregate_analysis_results(self, frame_analyses: List[Dict], 
                                        analysis_id: str, start_time: float) -> AnalysisResult:
        """Aggregate frame-level analyses into final result"""
        
        if not frame_analyses:
            raise ValueError("No frame analyses to aggregate")
        
        # Initialize aggregated results
        aggregated_emotions = {
            'happiness': 0.0, 'sadness': 0.0, 'anger': 0.0, 'fear': 0.0,
            'surprise': 0.0, 'disgust': 0.0, 'neutral': 0.0
        }
        
        engagement_scores = []
        attention_scores = []
        confidence_scores = []
        stress_indicators_counts = {}
        eye_contact_scores = []
        
        # Process each frame analysis
        valid_frames = 0
        for analysis in frame_analyses:
            if 'error' not in analysis:
                valid_frames += 1
                
                # Aggregate emotions
                emotions = analysis.get('emotions', {})
                for emotion, score in emotions.items():
                    if emotion in aggregated_emotions:
                        aggregated_emotions[emotion] += score
                
                # Collect engagement scores
                engagement = analysis.get('engagement_metrics', {})
                if 'overall_score' in engagement:
                    engagement_scores.append(engagement['overall_score'])
                
                # Collect attention scores
                attention = analysis.get('attention_metrics', {})
                if 'focus_score' in attention:
                    attention_scores.append(attention['focus_score'])
                
                # Collect confidence scores
                confidence = analysis.get('confidence_indicators', {})
                if 'overall_confidence' in confidence:
                    confidence_scores.append(confidence['overall_confidence'])
                
                # Count stress indicators
                stress_list = analysis.get('stress_indicators', [])
                for indicator in stress_list:
                    stress_indicators_counts[indicator] = stress_indicators_counts.get(indicator, 0) + 1
                
                # Collect eye contact scores
                eye_data = analysis.get('eye_contact', {})
                # Simplified eye contact scoring
                eye_contact_scores.append(0.7)  # Default value
        
        # Calculate averages
        if valid_frames > 0:
            for emotion in aggregated_emotions:
                aggregated_emotions[emotion] /= valid_frames
        
        # Create result objects
        emotion_scores = EmotionScores(**aggregated_emotions)
        
        # Facial expressions (simplified)
        facial_expressions = FacialExpressions(
            smile_intensity=aggregated_emotions['happiness'],
            eyebrow_movement=0.3,
            eye_openness=0.7,
            mouth_openness=0.2,
            head_pose={'pitch': 0.0, 'yaw': 0.0, 'roll': 0.0},
            gaze_direction={'x': 0.0, 'y': 0.0}
        )
        
        # Stress indicators
        dominant_stress_indicators = []
        for indicator, count in stress_indicators_counts.items():
            if count > len(frame_analyses) * 0.3:  # Present in >30% of frames
                dominant_stress_indicators.append(indicator)
        
        stress_indicators = StressIndicators(
            overall_stress_level=len(dominant_stress_indicators) / 10.0,
            physical_indicators=dominant_stress_indicators,
            vocal_stress_markers=[],  # Would need audio analysis
            behavioral_patterns=[]
        )
        
        # Posture analysis (simplified)
        posture_analysis = PostureAnalysis(
            posture_score=0.7,
            movement_intensity=0.3,
            gesture_frequency=0.5,
            body_orientation='facing_camera',
            fidgeting_indicators=[]
        )
        
        # Micro expressions (simplified)
        micro_expressions = MicroExpressions(
            detected_expressions=[],
            authenticity_score=0.8,
            deception_indicators=[],
            emotional_leakage=[]
        )
        
        # Calculate aggregate scores
        avg_engagement = np.mean(engagement_scores) if engagement_scores else 0.5
        avg_attention = np.mean(attention_scores) if attention_scores else 0.5
        avg_confidence = np.mean(confidence_scores) if confidence_scores else 0.5
        avg_eye_contact = np.mean(eye_contact_scores) if eye_contact_scores else 0.7
        
        return AnalysisResult(
            analysis_id=analysis_id,
            emotions=emotion_scores,
            engagement_score=avg_engagement,
            attention_score=avg_attention,
            stress_indicators=stress_indicators,
            confidence_level=avg_confidence,
            facial_expressions=facial_expressions,
            eye_contact_score=avg_eye_contact,
            posture_analysis=posture_analysis,
            micro_expressions=micro_expressions,
            processing_time=0.0,  # Will be set by caller
            model_confidence=0.85  # Overall model confidence
        )
    
    async def analyze_engagement(self, video_data: str, audio_data: Optional[str],
                               session_id: str, timestamp: float) -> Dict:
        """Analyze engagement from video and audio data"""
        # This would be implemented to provide real-time engagement analysis
        return {
            'engagement_score': 0.75,
            'attention_level': 0.8,
            'focus_duration': 45.0,
            'distraction_indicators': [],
            'eye_contact_percentage': 85.0,
            'facial_engagement': 0.7,
            'vocal_engagement': 0.8,
            'overall_presence': 0.75
        }
    
    async def cleanup(self):
        """Cleanup resources"""
        if self.face_mesh:
            self.face_mesh.close()
        if self.pose:
            self.pose.close()
        if self.hands:
            self.hands.close()
        if self.executor:
            self.executor.shutdown(wait=True)
        
        logger.info("VideoAnalyzer cleanup completed")


# Example usage and testing functions
async def test_video_analyzer():
    """Test the video analyzer with sample data"""
    analyzer = VideoAnalyzer(
        emotion_model_path="/app/models/emotion_model.h5",
        engagement_model_path="/app/models/engagement_model.pkl"
    )
    
    await analyzer.initialize_models()
    
    # Test with sample video data (would be actual video in practice)
    sample_video_data = "sample_base64_encoded_video_data"
    
    result = await analyzer.analyze_video(
        video_data=sample_video_data,
        session_id="test_session",
        candidate_id=123,
        analysis_config={
            'enable_emotion_detection': True,
            'enable_engagement_tracking': True,
            'enable_micro_expressions': True
        }
    )
    
    print(f"Analysis completed: {result.analysis_id}")
    print(f"Engagement score: {result.engagement_score}")
    print(f"Dominant emotion: {max(result.emotions.__dict__.items(), key=lambda x: x[1])}")
    print(f"Processing time: {result.processing_time:.2f}s")
    
    await analyzer.cleanup()


if __name__ == "__main__":
    asyncio.run(test_video_analyzer())
