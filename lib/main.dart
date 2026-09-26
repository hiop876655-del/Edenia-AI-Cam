import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:google_fonts/google_fonts.dart';
import 'blocs/camera_bloc.dart';
import 'blocs/recorder_bloc.dart';
import 'screens/camera_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Fullscreen immersive camera experience
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
      systemNavigationBarColor: Colors.black,
      systemNavigationBarIconBrightness: Brightness.light,
    ),
  );

  runApp(const AICameraApp());
}

class AICameraApp extends StatelessWidget {
  const AICameraApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiBlocProvider(
      providers: [
        BlocProvider<CameraBloc>(
          create: (context) => CameraBloc()..add(InitializeCameraEvent()),
        ),
        BlocProvider<RecorderBloc>(
          create: (context) => RecorderBloc()..add(InitializeRecorderEvent()),
        ),
      ],
      child: MaterialApp(
        title: 'AI Camera & Screen Recorder Pro',
        debugShowCheckedModeBanner: false,
        themeMode: ThemeMode.dark,
        theme: ThemeData(
          brightness: Brightness.dark,
          scaffoldBackgroundColor: const Color(0xFF0B0F17),
          primaryColor: const Color(0xFF38BDF8),
          colorScheme: const ColorScheme.dark(
            primary: Color(0xFF38BDF8),
            secondary: Color(0xFF818CF8),
            surface: Color(0xFF131B2B),
            background: Color(0xFF0B0F17),
          ),
          textTheme: GoogleFonts.cairoTextTheme(ThemeData.dark().textTheme),
        ),
        home: const CameraScreen(),
      ),
    );
  }
}
